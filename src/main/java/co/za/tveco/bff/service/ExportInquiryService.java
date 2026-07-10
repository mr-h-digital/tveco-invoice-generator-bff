package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.ClientExportInquiryRequest;
import co.za.tveco.bff.dto.ExportInquiryDto;
import co.za.tveco.bff.dto.ExportInquiryMessageDto;
import co.za.tveco.bff.dto.ExportJobClientSnapshotDto;
import co.za.tveco.bff.dto.ExportJobCreateRequest;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.dto.InquiryMessageCreateRequest;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.Client;
import co.za.tveco.bff.entity.ExportInquiry;
import co.za.tveco.bff.entity.ExportInquiryMessage;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.entity.Quote;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.ForbiddenException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ClientRepository;
import co.za.tveco.bff.repository.ExportInquiryMessageRepository;
import co.za.tveco.bff.repository.ExportInquiryRepository;
import co.za.tveco.bff.repository.ExportJobRepository;
import co.za.tveco.bff.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportInquiryService {

    private static final List<String> INQUIRY_TYPES = List.of("INQUIRY", "REQUEST");
    private static final List<String> INQUIRY_STATUSES = List.of(
            "SUBMITTED",
            "UNDER_REVIEW",
            "WAITING_ON_CLIENT",
            "READY_FOR_QUOTE",
            "QUOTED",
            "CONVERTED_TO_JOB",
            "CLOSED"
    );

    private final AppUserRepository appUserRepository;
    private final ClientRepository clientRepository;
    private final QuoteRepository quoteRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportInquiryRepository exportInquiryRepository;
    private final ExportInquiryMessageRepository inquiryMessageRepository;
    private final ExportJobService exportJobService;

    @Transactional(readOnly = true)
    public List<ExportInquiryDto> getAllForAdmin(String email) {
        requireAdminUser(email);
        return exportInquiryRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExportInquiryDto getByIdForAdmin(String email, UUID inquiryId) {
        requireAdminUser(email);
        return toDto(findInquiryOrThrow(inquiryId));
    }

    @Transactional(readOnly = true)
    public List<ExportInquiryDto> getForClient(String email) {
        AppUser user = requireClientUser(email);
        return exportInquiryRepository.findByClientIdOrderByCreatedAtDesc(user.getClientId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ExportInquiryDto createForClient(String email, ClientExportInquiryRequest req) {
        AppUser user = requireClientUser(email);
        Client client = clientRepository.findById(user.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client profile not found"));

        String inquiryType = req.inquiryType().trim().toUpperCase(Locale.ROOT);
        if (!INQUIRY_TYPES.contains(inquiryType)) {
            throw new IllegalArgumentException("inquiryType must be INQUIRY or REQUEST");
        }

        String inquiryNumber = nextInquiryNumber();
        LocalDate departure = parseDateOrNull(req.estimatedDepartureDate());
        LocalDate arrival = parseDateOrNull(req.estimatedArrivalDate());

        ExportInquiry inquiry = exportInquiryRepository.save(ExportInquiry.builder()
                .inquiryNumber(inquiryNumber)
                .clientId(client.getId())
                .inquiryType(inquiryType)
                .status("SUBMITTED")
                .sourceChannel("Website")
                .destinationCountry(req.destinationCountry().trim())
                .vehicleDescription(req.vehicleDescription().trim())
                .projectValue(req.projectValue())
                .estimatedDepartureDate(departure)
                .estimatedArrivalDate(arrival)
                .notes(req.notes() == null ? "" : req.notes().trim())
                .build());

        if (req.notes() != null && !req.notes().isBlank()) {
            inquiryMessageRepository.save(ExportInquiryMessage.builder()
                    .inquiryId(inquiry.getId())
                    .senderRole("CLIENT")
                    .senderEmail(client.getEmail())
                    .message(req.notes().trim())
                    .requiresClientResponse(false)
                    .clientResponded(false)
                    .build());
        }

        return toDto(inquiry);
    }

    @Transactional
    public ExportInquiryDto addAdminMessage(String email, UUID inquiryId, InquiryMessageCreateRequest req) {
        AppUser admin = requireAdminUser(email);
        ExportInquiry inquiry = findInquiryOrThrow(inquiryId);

        boolean requiresClientResponse = req.requiresClientResponse() != null && req.requiresClientResponse();
        inquiryMessageRepository.save(ExportInquiryMessage.builder()
                .inquiryId(inquiry.getId())
                .senderRole("ADMIN")
                .senderEmail(admin.getEmail())
                .message(req.message().trim())
                .requiresClientResponse(requiresClientResponse)
                .clientResponded(false)
                .build());

        if (requiresClientResponse) {
            inquiry.setStatus("WAITING_ON_CLIENT");
        } else if ("SUBMITTED".equals(inquiry.getStatus())) {
            inquiry.setStatus("UNDER_REVIEW");
        }

        return toDto(exportInquiryRepository.save(inquiry));
    }

    @Transactional
    public ExportInquiryDto addClientResponse(String email, UUID inquiryId, InquiryMessageCreateRequest req) {
        AppUser user = requireClientUser(email);
        ExportInquiry inquiry = findInquiryOrThrow(inquiryId);
        if (!inquiry.getClientId().equals(user.getClientId())) {
            throw new ForbiddenException("You can only respond to your own inquiries");
        }

        inquiryMessageRepository.save(ExportInquiryMessage.builder()
                .inquiryId(inquiry.getId())
                .senderRole("CLIENT")
                .senderEmail(user.getEmail())
                .message(req.message().trim())
                .requiresClientResponse(false)
                .clientResponded(false)
                .build());

        List<ExportInquiryMessage> messages = inquiryMessageRepository.findByInquiryIdOrderByCreatedAtAsc(inquiry.getId());
        for (int i = messages.size() - 1; i >= 0; i--) {
            ExportInquiryMessage msg = messages.get(i);
            if (msg.isRequiresClientResponse() && !msg.isClientResponded()) {
                msg.setClientResponded(true);
                inquiryMessageRepository.save(msg);
                break;
            }
        }

        inquiry.setStatus("UNDER_REVIEW");
        return toDto(exportInquiryRepository.save(inquiry));
    }

    @Transactional
    public ExportInquiryDto updateStatusForAdmin(String email, UUID inquiryId, String status) {
        requireAdminUser(email);
        updateStatusInternal(inquiryId, status);
        return toDto(findInquiryOrThrow(inquiryId));
    }

    @Transactional
    void updateStatusInternal(UUID inquiryId, String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!INQUIRY_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid inquiry status");
        }

        ExportInquiry inquiry = findInquiryOrThrow(inquiryId);
        inquiry.setStatus(normalized);
        exportInquiryRepository.save(inquiry);
    }

    @Transactional
    public ExportJobDto convertAcceptedQuoteToJob(String email, UUID inquiryId, UUID quoteId) {
        requireAdminUser(email);

        ExportInquiry inquiry = findInquiryOrThrow(inquiryId);
        if ("CONVERTED_TO_JOB".equals(inquiry.getStatus())) {
            throw new ConflictException("Inquiry has already been converted to an export job");
        }

        if (exportJobRepository.findByInquiryId(inquiryId).isPresent()) {
            throw new ConflictException("Inquiry has already been converted to an export job");
        }

        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found: " + quoteId));

        if (!"ACCEPTED".equals(quote.getStatus())) {
            throw new ConflictException("Only accepted quotes can be converted to export jobs");
        }
        if (quote.getInquiryId() == null || !quote.getInquiryId().equals(inquiryId)) {
            throw new ConflictException("Quote is not linked to the selected inquiry");
        }

        Client client = clientRepository.findById(inquiry.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found for inquiry"));

        ExportJobCreateRequest createRequest = new ExportJobCreateRequest(
                client.getId(),
                quote.getId(),
                inquiry.getId(),
                new ExportJobClientSnapshotDto(
                        client.getCompanyName(),
                        client.getContactName(),
                        client.getEmail(),
                        client.getPhone()
                ),
                inquiry.getDestinationCountry(),
                inquiry.getVehicleDescription(),
                inquiry.getSourceChannel(),
                quote.getTotal(),
                null,
                null,
                null,
                inquiry.getEstimatedDepartureDate() == null ? null : inquiry.getEstimatedDepartureDate().toString(),
                inquiry.getEstimatedArrivalDate() == null ? null : inquiry.getEstimatedArrivalDate().toString(),
                inquiry.getNotes()
        );

        ExportJobDto job = exportJobService.create(createRequest);
        inquiry.setStatus("CONVERTED_TO_JOB");
        exportInquiryRepository.save(inquiry);
        return job;
    }

    private ExportInquiry findInquiryOrThrow(UUID inquiryId) {
        return exportInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Export inquiry not found: " + inquiryId));
    }

    private AppUser requireAdminUser(String email) {
        AppUser user = getUserByEmail(email);
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new ForbiddenException("This action is only available to admin users");
        }
        return user;
    }

    private AppUser requireClientUser(String email) {
        AppUser user = getUserByEmail(email);
        if (!"client".equalsIgnoreCase(user.getRole()) || user.getClientId() == null) {
            throw new ForbiddenException("This action is only available to client users");
        }
        return user;
    }

    private AppUser getUserByEmail(String email) {
        return appUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ForbiddenException("User account not found"));
    }

    private String nextInquiryNumber() {
        String year = String.valueOf(LocalDate.now().getYear());
        String prefix = "INQ-" + year + "-";
        int nextSequence = exportInquiryRepository.findByInquiryNumberStartingWith(prefix).stream()
                .map(ExportInquiry::getInquiryNumber)
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
        return String.format("%s%03d", prefix, nextSequence);
    }

    private LocalDate parseDateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private ExportInquiryDto toDto(ExportInquiry inquiry) {
        List<ExportInquiryMessageDto> messages = inquiryMessageRepository.findByInquiryIdOrderByCreatedAtAsc(inquiry.getId()).stream()
                .map(msg -> new ExportInquiryMessageDto(
                        msg.getId(),
                        msg.getInquiryId(),
                        msg.getSenderRole(),
                        msg.getSenderEmail(),
                        msg.getMessage(),
                        msg.isRequiresClientResponse(),
                        msg.isClientResponded(),
                        msg.getCreatedAt()
                ))
                .toList();

        Quote latestQuote = quoteRepository.findByInquiryIdOrderByCreatedAtDesc(inquiry.getId()).stream().findFirst().orElse(null);
        ExportJob linkedJob = exportJobRepository.findByInquiryId(inquiry.getId()).orElse(null);

        return new ExportInquiryDto(
                inquiry.getId(),
                inquiry.getInquiryNumber(),
                inquiry.getClientId(),
                inquiry.getInquiryType(),
                inquiry.getStatus(),
                inquiry.getSourceChannel(),
                inquiry.getDestinationCountry(),
                inquiry.getVehicleDescription(),
                inquiry.getProjectValue(),
                inquiry.getEstimatedDepartureDate(),
                inquiry.getEstimatedArrivalDate(),
                inquiry.getNotes(),
                messages,
                latestQuote == null ? null : latestQuote.getId(),
                latestQuote == null ? null : latestQuote.getQuoteNumber(),
                latestQuote == null ? null : latestQuote.getStatus(),
                linkedJob == null ? null : linkedJob.getId(),
                linkedJob == null ? null : linkedJob.getJobNumber(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt()
        );
    }
}
