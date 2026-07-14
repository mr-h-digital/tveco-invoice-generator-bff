package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.DocumentDownloadUrlResponse;
import co.za.tveco.bff.dto.DocumentUploadCompleteRequest;
import co.za.tveco.bff.dto.DocumentUploadInitRequest;
import co.za.tveco.bff.dto.DocumentUploadInitResponse;
import co.za.tveco.bff.dto.ExportJobCreateRequest;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.service.ExportJobDocumentService;
import co.za.tveco.bff.service.ExportJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/export-jobs")
@RequiredArgsConstructor
public class ExportJobController {

    private final ExportJobService exportJobService;
    private final ExportJobDocumentService exportJobDocumentService;

    @GetMapping
    public ApiResponse<List<ExportJobDto>> getAll() {
        return ApiResponse.of(exportJobService.getAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExportJobDto> create(@Valid @RequestBody ExportJobCreateRequest req) {
        return ApiResponse.of(exportJobService.create(req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ExportJobDto> patch(@PathVariable UUID id, @RequestBody Map<String, Object> patch) {
        return ApiResponse.of(exportJobService.patch(id, patch));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<ExportJobDto> cancel(@PathVariable UUID id, @RequestBody Map<String, String> payload) {
        String reason = payload == null ? null : payload.get("reason");
        return ApiResponse.of(exportJobService.patch(id, Map.of(
                "status", "CANCELLED",
                "cancellationReason", reason == null ? "" : reason
        )));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        exportJobService.delete(id);
    }

    @GetMapping("/tracking/{token}")
    public ApiResponse<ExportJobDto> getByTrackingToken(@PathVariable String token) {
        return ApiResponse.of(exportJobService.findByTrackingToken(token));
    }

    @PostMapping("/tracking/{token}/documents/{documentId}/download-url")
    public ApiResponse<DocumentDownloadUrlResponse> createTrackingDownloadUrl(
            @PathVariable String token,
            @PathVariable UUID documentId
    ) {
        return ApiResponse.of(exportJobDocumentService.createTrackingDownloadUrl(token, documentId));
    }

    @PostMapping("/{id}/documents/init-upload")
    public ApiResponse<DocumentUploadInitResponse> initUpload(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUploadInitRequest req,
            Authentication authentication
    ) {
        return ApiResponse.of(exportJobDocumentService.initAdminUpload(authentication.getName(), id, req));
    }

    @PostMapping("/{id}/documents/{documentId}/complete-upload")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeUpload(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestBody(required = false) DocumentUploadCompleteRequest req,
            Authentication authentication
    ) {
        exportJobDocumentService.completeAdminUpload(authentication.getName(), id, documentId, req == null ? new DocumentUploadCompleteRequest(null) : req);
    }

    @PostMapping("/{id}/documents/{documentId}/download-url")
    public ApiResponse<DocumentDownloadUrlResponse> createDownloadUrl(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return ApiResponse.of(exportJobDocumentService.createAdminDownloadUrl(authentication.getName(), id, documentId));
    }
}