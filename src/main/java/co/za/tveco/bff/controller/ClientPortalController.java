package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.ClientDocumentUploadRequest;
import co.za.tveco.bff.dto.ClientExportJobRequest;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.service.ClientPortalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/client-portal")
@RequiredArgsConstructor
public class ClientPortalController {

    private final ClientPortalService clientPortalService;

    @GetMapping("/jobs")
    public ApiResponse<List<ExportJobDto>> getMyJobs(Authentication authentication) {
        return ApiResponse.of(clientPortalService.getMyJobs(authentication.getName()));
    }

    @PostMapping("/requests")
    public ApiResponse<ExportJobDto> submitExportRequest(@Valid @RequestBody ClientExportJobRequest req, Authentication authentication) {
        return ApiResponse.of(clientPortalService.createRequest(authentication.getName(), req));
    }

    @PostMapping("/jobs/{id}/documents")
    public ApiResponse<ExportJobDto> uploadDocument(
            @PathVariable UUID id,
            @Valid @RequestBody ClientDocumentUploadRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(clientPortalService.uploadDocument(authentication.getName(), id, req));
    }
}
