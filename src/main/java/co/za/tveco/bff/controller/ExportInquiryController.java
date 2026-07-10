package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.ExportInquiryDto;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.dto.InquiryConvertToJobRequest;
import co.za.tveco.bff.dto.InquiryMessageCreateRequest;
import co.za.tveco.bff.dto.InquiryStatusUpdateRequest;
import co.za.tveco.bff.service.ExportInquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/export-inquiries")
@RequiredArgsConstructor
public class ExportInquiryController {

    private final ExportInquiryService exportInquiryService;

    @GetMapping
    public ApiResponse<List<ExportInquiryDto>> getAll(Authentication authentication) {
        return ApiResponse.of(exportInquiryService.getAllForAdmin(authentication.getName()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExportInquiryDto> getById(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.of(exportInquiryService.getByIdForAdmin(authentication.getName(), id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<ExportInquiryDto> addAdminMessage(
            @PathVariable UUID id,
            @Valid @RequestBody InquiryMessageCreateRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(exportInquiryService.addAdminMessage(authentication.getName(), id, req));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ExportInquiryDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody InquiryStatusUpdateRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(exportInquiryService.updateStatusForAdmin(authentication.getName(), id, req.status()));
    }

    @PostMapping("/{id}/convert-to-job")
    public ApiResponse<ExportJobDto> convertToJob(
            @PathVariable UUID id,
            @Valid @RequestBody InquiryConvertToJobRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(exportInquiryService.convertAcceptedQuoteToJob(authentication.getName(), id, req.quoteId()));
    }
}
