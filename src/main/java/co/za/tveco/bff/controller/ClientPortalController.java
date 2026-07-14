package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.ClientExportInquiryRequest;
import co.za.tveco.bff.dto.ClientQuoteDecisionRequest;
import co.za.tveco.bff.dto.DocumentDownloadUrlResponse;
import co.za.tveco.bff.dto.DocumentUploadCompleteRequest;
import co.za.tveco.bff.dto.DocumentUploadInitRequest;
import co.za.tveco.bff.dto.DocumentUploadInitResponse;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.dto.ExportInquiryDto;
import co.za.tveco.bff.dto.InquiryMessageCreateRequest;
import co.za.tveco.bff.dto.QuoteDto;
import co.za.tveco.bff.service.ExportJobDocumentService;
import co.za.tveco.bff.service.ClientPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client-portal")
@RequiredArgsConstructor
public class ClientPortalController {

    private final ClientPortalService clientPortalService;
    private final ExportJobDocumentService exportJobDocumentService;

    @GetMapping("/jobs")
    public ApiResponse<List<ExportJobDto>> getMyJobs(Authentication authentication) {
        return ApiResponse.of(clientPortalService.getMyJobs(authentication.getName()));
    }

    @GetMapping("/inquiries")
    public ApiResponse<List<ExportInquiryDto>> getMyInquiries(Authentication authentication) {
        return ApiResponse.of(clientPortalService.getMyInquiries(authentication.getName()));
    }

    @PostMapping("/inquiries")
    public ApiResponse<ExportInquiryDto> submitExportInquiry(
            @Valid @RequestBody ClientExportInquiryRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(clientPortalService.createInquiry(authentication.getName(), req));
    }

    @PostMapping("/inquiries/{id}/responses")
    public ApiResponse<ExportInquiryDto> respondToInquiry(
            @PathVariable UUID id,
            @Valid @RequestBody InquiryMessageCreateRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(clientPortalService.respondToInquiry(authentication.getName(), id, req));
    }

    @GetMapping("/quotes")
    public ApiResponse<List<QuoteDto>> getMyQuotes(Authentication authentication) {
        return ApiResponse.of(clientPortalService.getMyQuotes(authentication.getName()));
    }

    @PostMapping("/quotes/{id}/decision")
    public ApiResponse<QuoteDto> decideQuote(
            @PathVariable UUID id,
            @Valid @RequestBody ClientQuoteDecisionRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(clientPortalService.decideQuote(authentication.getName(), id, req));
    }

    @PostMapping("/jobs/{id}/documents")
    public void uploadDocumentDeprecated(
            @PathVariable UUID id,
            @RequestBody(required = false) Object ignored,
            Authentication authentication
    ) {
        throw new ResponseStatusException(
                HttpStatus.GONE,
                "Endpoint deprecated. Use /api/client-portal/jobs/{id}/documents/init-upload and /complete-upload."
        );
    }

    @PostMapping("/jobs/{id}/documents/init-upload")
    public ApiResponse<DocumentUploadInitResponse> initDocumentUpload(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUploadInitRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(exportJobDocumentService.initClientUpload(authentication.getName(), id, req));
    }

    @PostMapping("/jobs/{id}/documents/{documentId}/complete-upload")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeDocumentUpload(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestBody(required = false) DocumentUploadCompleteRequest req,
            Authentication authentication
    ) {
        exportJobDocumentService.completeClientUpload(authentication.getName(), id, documentId, req == null ? new DocumentUploadCompleteRequest(null) : req);
    }

    @PostMapping("/jobs/{id}/documents/{documentId}/download-url")
    public ApiResponse<DocumentDownloadUrlResponse> createDocumentDownloadUrl(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return ApiResponse.of(exportJobDocumentService.createClientDownloadUrl(authentication.getName(), id, documentId));
    }
}
