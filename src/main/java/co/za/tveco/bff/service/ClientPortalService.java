package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.ClientExportInquiryRequest;
import co.za.tveco.bff.dto.ClientQuoteDecisionRequest;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.dto.ExportInquiryDto;
import co.za.tveco.bff.dto.InquiryMessageCreateRequest;
import co.za.tveco.bff.dto.QuoteDto;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.ExportJob;
import co.za.tveco.bff.entity.Quote;
import co.za.tveco.bff.exception.ForbiddenException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ExportJobRepository;
import co.za.tveco.bff.repository.QuoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientPortalService {

    private final AppUserRepository appUserRepository;
    private final ExportJobRepository exportJobRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteService quoteService;
    private final ExportInquiryService exportInquiryService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ExportJobDto> getMyJobs(String email) {
        AppUser user = getClientUser(email);
        return exportJobRepository.findByClientIdOrderByCreatedAtDesc(user.getClientId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExportInquiryDto> getMyInquiries(String email) {
        return exportInquiryService.getForClient(email);
    }

    @Transactional
    public ExportInquiryDto createInquiry(String email, ClientExportInquiryRequest req) {
        return exportInquiryService.createForClient(email, req);
    }

    @Transactional
    public ExportInquiryDto respondToInquiry(String email, UUID inquiryId, InquiryMessageCreateRequest req) {
        return exportInquiryService.addClientResponse(email, inquiryId, req);
    }

    @Transactional(readOnly = true)
    public List<QuoteDto> getMyQuotes(String email) {
        AppUser user = getClientUser(email);
        return quoteRepository.findByClientIdOrderByCreatedAtDesc(user.getClientId()).stream()
                .map(quoteService::toDto)
                .toList();
    }

    @Transactional
    public QuoteDto decideQuote(String email, UUID quoteId, ClientQuoteDecisionRequest req) {
        AppUser user = getClientUser(email);
        Quote quote = quoteRepository.findByIdAndClientId(quoteId, user.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        if (!"SENT".equals(quote.getStatus())) {
            throw new IllegalArgumentException("Only SENT quotes can be accepted or declined");
        }

        String normalized = req.status().trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPTED".equals(normalized) && !"DECLINED".equals(normalized) && !"REJECTED".equals(normalized)) {
            throw new IllegalArgumentException("Status must be ACCEPTED or DECLINED");
        }

        quote.setStatus("ACCEPTED".equals(normalized) ? "ACCEPTED" : "REJECTED");
        quote.setClientDecisionAt(Instant.now());
        quote.setClientDecisionNote(req.note() == null ? null : req.note().trim());
        Quote saved = quoteRepository.save(quote);
        quoteService.syncInquiryStatusFromQuote(saved);

        return quoteService.toDto(saved);
    }

    private AppUser getClientUser(String email) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ForbiddenException("Client account not found"));

        if (!"client".equalsIgnoreCase(user.getRole()) || user.getClientId() == null) {
            throw new ForbiddenException("Client portal is only available for client accounts");
        }

        return user;
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
                e.getQuoteId(),
                e.getInquiryId(),
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
