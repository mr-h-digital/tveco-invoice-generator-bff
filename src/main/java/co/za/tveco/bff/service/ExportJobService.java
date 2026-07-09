package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.ExportJobCreateRequest;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.ExportJobRepository;
import co.za.tveco.bff.repository.InvoiceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportJobService {

    private static final List<String> STATUS_ORDER = List.of("ENQUIRY", "SOURCING", "DOCUMENTATION", "SHIPPING", "DELIVERED");
    private static final List<String> ALL_STATUSES = List.of("ENQUIRY", "SOURCING", "DOCUMENTATION", "SHIPPING", "DELIVERED", "CANCELLED");
    private static final List<String> CORE_EDITABLE_STATUSES = List.of("ENQUIRY", "SOURCING", "DOCUMENTATION");
    private static final List<String> TERMINAL_STATUSES = List.of("DELIVERED", "CANCELLED");
    private static final List<String> SOURCE_CHANNELS = List.of("Website", "WhatsApp", "Referral", "Direct");
    private static final List<String> CORE_FIELDS = List.of(
            "clientId",
            "clientSnapshot",
            "destinationCountry",
            "vehicleDescription",
            "sourceChannel",
            "projectValue",
            "estimatedDepartureDate",
            "estimatedArrivalDate"
    );

    private final ExportJobRepository exportJobRepository;
    private final InvoiceRepository invoiceRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ExportJobDto> getAll() {
        return exportJobRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ExportJobDto create(ExportJobCreateRequest req) {
        LocalDate issueDate = LocalDate.now();
        String year = String.valueOf(issueDate.getYear());
        String prefix = "TVECO-EXP-" + year + "-";
        int nextSequence = exportJobRepository.findByJobNumberStartingWith(prefix).stream()
                .map(ExportJob::getJobNumber)
                .map(number -> number.substring(prefix.length()))
                .mapToInt(sequence -> {
                    try {
                        return Integer.parseInt(sequence);
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .max()
                .orElse(0) + 1;
        String jobNumber = String.format("%s%03d", prefix, nextSequence);
        String token = "TVC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        BigDecimal projectValue = req.projectValue().setScale(2, RoundingMode.HALF_UP);
        LocalDate departure = req.estimatedDepartureDate() == null || req.estimatedDepartureDate().isBlank()
                ? issueDate.plusDays(7)
                : LocalDate.parse(req.estimatedDepartureDate());
        LocalDate arrival = req.estimatedArrivalDate() == null || req.estimatedArrivalDate().isBlank()
                ? issueDate.plusDays(35)
                : LocalDate.parse(req.estimatedArrivalDate());

        ExportJob entity = ExportJob.builder()
                .jobNumber(jobNumber)
                .publicTrackingToken(token)
                .clientId(req.clientId())
                .clientSnapshot(writeJson(buildClientSnapshot(req)))
                .destinationCountry(req.destinationCountry().trim())
                .vehicleDescription(req.vehicleDescription().trim())
                .sourceChannel(req.sourceChannel())
                .projectValue(projectValue)
                .status("ENQUIRY")
                .milestones(writeJson(defaultMilestones(issueDate)))
                .documents(writeJson(defaultDocuments()))
                .paymentMilestones(writeJson(buildPaymentMilestones(projectValue, issueDate)))
                .vaultDocuments(writeJson(objectMapper.createArrayNode()))
                .estimatedDepartureDate(departure)
                .estimatedArrivalDate(arrival)
                .notes(req.notes() == null ? "" : req.notes())
                .cancellationReason(null)
                .build();

        return toDto(exportJobRepository.save(entity));
    }

    @Transactional
    public ExportJobDto patch(UUID id, Map<String, Object> patch) {
        ExportJob job = findOrThrow(id);

        if (TERMINAL_STATUSES.contains(job.getStatus())) {
            throw new ConflictException("Export job can no longer be edited once it is " + job.getStatus());
        }

        if (!CORE_EDITABLE_STATUSES.contains(job.getStatus())) {
            boolean hasCoreFieldChanges = patch.keySet().stream().anyMatch(CORE_FIELDS::contains);
            if (hasCoreFieldChanges) {
                throw new ConflictException("Core export job details cannot be edited once shipping has started");
            }
        }

        if (patch.containsKey("clientId")) {
            Object value = patch.get("clientId");
            if (value == null || String.valueOf(value).isBlank()) {
                job.setClientId(null);
            } else {
                job.setClientId(UUID.fromString(String.valueOf(value)));
            }
        }

        if (patch.containsKey("clientSnapshot")) {
            job.setClientSnapshot(writeJson(objectMapper.valueToTree(patch.get("clientSnapshot"))));
        }
        if (patch.containsKey("destinationCountry")) {
            job.setDestinationCountry(String.valueOf(patch.get("destinationCountry")));
        }
        if (patch.containsKey("vehicleDescription")) {
            job.setVehicleDescription(String.valueOf(patch.get("vehicleDescription")));
        }
        if (patch.containsKey("sourceChannel")) {
            String sourceChannel = String.valueOf(patch.get("sourceChannel"));
            if (!SOURCE_CHANNELS.contains(sourceChannel)) {
                throw new IllegalArgumentException("Invalid sourceChannel");
            }
            job.setSourceChannel(sourceChannel);
        }
        if (patch.containsKey("projectValue")) {
            BigDecimal value = new BigDecimal(String.valueOf(patch.get("projectValue")));
            job.setProjectValue(value.setScale(2, RoundingMode.HALF_UP));
        }
        if (patch.containsKey("status")) {
            String status = String.valueOf(patch.get("status"));
            if (!ALL_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Invalid export job status");
            }

            if (!status.equals(job.getStatus())) {
                int currentIdx = STATUS_ORDER.indexOf(job.getStatus());
                int nextIdx = STATUS_ORDER.indexOf(status);

                if (!"CANCELLED".equals(status)) {
                    if (currentIdx < 0 || nextIdx < 0 || nextIdx != currentIdx + 1) {
                        throw new ConflictException("Status can only move forward one stage at a time");
                    }
                }
            }

            if ("CANCELLED".equals(status)) {
                Object reasonValue = patch.get("cancellationReason");
                String reason = reasonValue == null ? "" : String.valueOf(reasonValue).trim();
                if (reason.isBlank()) {
                    throw new IllegalArgumentException("Cancellation reason is required when cancelling an export job");
                }
                job.setCancellationReason(reason);
            } else {
                job.setCancellationReason(null);
            }

            job.setStatus(status);
        }

        if (patch.containsKey("cancellationReason") && !"CANCELLED".equals(job.getStatus())) {
            throw new IllegalArgumentException("Cancellation reason can only be set when status is CANCELLED");
        }

        if (patch.containsKey("cancellationReason") && "CANCELLED".equals(job.getStatus())) {
            String reason = String.valueOf(patch.get("cancellationReason")).trim();
            if (reason.isBlank()) {
                throw new IllegalArgumentException("Cancellation reason is required when cancelling an export job");
            }
            job.setCancellationReason(reason);
        }
        if (patch.containsKey("milestones")) {
            job.setMilestones(writeJson(objectMapper.valueToTree(patch.get("milestones"))));
        }
        if (patch.containsKey("documents")) {
            job.setDocuments(writeJson(objectMapper.valueToTree(patch.get("documents"))));
        }
        if (patch.containsKey("paymentMilestones")) {
            job.setPaymentMilestones(writeJson(objectMapper.valueToTree(patch.get("paymentMilestones"))));
        }
        if (patch.containsKey("vaultDocuments")) {
            job.setVaultDocuments(writeJson(objectMapper.valueToTree(patch.get("vaultDocuments"))));
        }
        if (patch.containsKey("estimatedDepartureDate")) {
            job.setEstimatedDepartureDate(LocalDate.parse(String.valueOf(patch.get("estimatedDepartureDate"))));
        }
        if (patch.containsKey("estimatedArrivalDate")) {
            job.setEstimatedArrivalDate(LocalDate.parse(String.valueOf(patch.get("estimatedArrivalDate"))));
        }
        if (patch.containsKey("notes")) {
            job.setNotes(String.valueOf(patch.get("notes")));
        }

        normalizeMilestonesForStatus(job);
        return toDto(exportJobRepository.save(job));
    }

    @Transactional
    public void delete(UUID id) {
        ExportJob job = findOrThrow(id);
        if (!"ENQUIRY".equals(job.getStatus())) {
            throw new ConflictException("Only ENQUIRY export jobs can be deleted");
        }

        long linkedInvoices = invoiceRepository.countByExportJobId(id);
        if (linkedInvoices > 0) {
            throw new ConflictException("Cannot delete export job because it has linked invoices");
        }

        exportJobRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ExportJobDto findByTrackingToken(String token) {
        ExportJob job = exportJobRepository.findByPublicTrackingTokenIgnoreCase(token)
                .orElseThrow(() -> new ResourceNotFoundException("No export job found for token " + token));
        return toDto(job);
    }

    private ExportJob findOrThrow(UUID id) {
        return exportJobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found: " + id));
    }

    private ObjectNode buildClientSnapshot(ExportJobCreateRequest req) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("companyName", req.clientSnapshot().companyName());
        node.put("contactName", req.clientSnapshot().contactName());
        node.put("email", req.clientSnapshot().email());
        node.put("phone", req.clientSnapshot().phone());
        return node;
    }

    private ArrayNode defaultMilestones(LocalDate now) {
        ArrayNode arr = objectMapper.createArrayNode();
        arr.add(milestone("enquiry", "Enquiry Received", now.atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toString()));
        arr.add(milestone("sourcing", "Vehicle Sourcing", null));
        arr.add(milestone("documentation", "Export Documentation", null));
        arr.add(milestone("shipping", "Shipping in Progress", null));
        arr.add(milestone("delivery", "Delivered", null));
        return arr;
    }

    private ObjectNode milestone(String key, String label, String completedAt) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("key", key);
        n.put("label", label);
        if (completedAt == null) {
            n.putNull("completedAt");
        } else {
            n.put("completedAt", completedAt);
        }
        return n;
    }

    private ArrayNode defaultDocuments() {
        ArrayNode arr = objectMapper.createArrayNode();
        arr.add(document("itac", "ITAC Permit", true, false));
        arr.add(document("sarpco", "SARPCO Clearance", true, false));
        arr.add(document("sadac", "SADAC Certificate", false, false));
        arr.add(document("invoice", "Commercial Invoice", true, false));
        arr.add(document("customs", "Customs Pack", true, false));
        return arr;
    }

    private ObjectNode document(String key, String label, boolean required, boolean completed) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("key", key);
        n.put("label", label);
        n.put("required", required);
        n.put("completed", completed);
        return n;
    }

    private ArrayNode buildPaymentMilestones(BigDecimal projectValue, LocalDate issueDate) {
        BigDecimal deposit = projectValue.multiply(BigDecimal.valueOf(0.30)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = projectValue.multiply(BigDecimal.valueOf(0.40)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = projectValue.subtract(deposit).subtract(shipping).setScale(2, RoundingMode.HALF_UP);

        ArrayNode arr = objectMapper.createArrayNode();
        arr.add(paymentMilestone("deposit", "Deposit (30%)", deposit, issueDate));
        arr.add(paymentMilestone("shipping", "Shipping Payment (40%)", shipping, issueDate.plusDays(10)));
        arr.add(paymentMilestone("balance", "Final Balance (30%)", balance, issueDate.plusDays(28)));
        return arr;
    }

    private ObjectNode paymentMilestone(String key, String label, BigDecimal amount, LocalDate dueDate) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("key", key);
        n.put("label", label);
        n.put("amount", amount);
        n.put("dueDate", dueDate.toString());
        n.put("paid", false);
        n.putNull("paidAt");
        return n;
    }

    private void normalizeMilestonesForStatus(ExportJob job) {
        JsonNode milestones = readJson(job.getMilestones(), objectMapper.createArrayNode());
        if (!milestones.isArray()) {
            return;
        }

        int activeIdx = STATUS_ORDER.indexOf(job.getStatus());
        if (activeIdx < 0) {
            return;
        }

        String now = java.time.Instant.now().toString();
        ArrayNode normalized = objectMapper.createArrayNode();
        for (int i = 0; i < milestones.size(); i++) {
            JsonNode original = milestones.get(i);
            ObjectNode next = original.deepCopy();
            JsonNode completedAt = original.get("completedAt");
            if (i <= activeIdx && (completedAt == null || completedAt.isNull() || completedAt.asText().isBlank())) {
                next.put("completedAt", now);
            }
            if (i > activeIdx && completedAt != null && !completedAt.isNull() && !completedAt.asText().isBlank()) {
                next.putNull("completedAt");
            }
            normalized.add(next);
        }
        job.setMilestones(writeJson(normalized));
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize JSON payload", e);
        }
    }

    private JsonNode readJson(String raw, JsonNode fallback) {
        try {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return fallback;
        }
    }

    private ExportJobDto toDto(ExportJob e) {
        return new ExportJobDto(
                e.getId(),
                e.getJobNumber(),
                e.getPublicTrackingToken(),
                e.getClientId(),
                readJson(e.getClientSnapshot(), objectMapper.createObjectNode()),
                e.getDestinationCountry(),
                e.getVehicleDescription(),
                e.getSourceChannel(),
                e.getProjectValue(),
                e.getStatus(),
                readJson(e.getMilestones(), objectMapper.createArrayNode()),
                readJson(e.getDocuments(), objectMapper.createArrayNode()),
                readJson(e.getPaymentMilestones(), objectMapper.createArrayNode()),
                readJson(e.getVaultDocuments(), objectMapper.createArrayNode()),
                e.getEstimatedDepartureDate(),
                e.getEstimatedArrivalDate(),
                e.getNotes(),
                e.getCancellationReason(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}