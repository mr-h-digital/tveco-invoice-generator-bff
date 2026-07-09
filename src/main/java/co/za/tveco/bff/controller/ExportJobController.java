package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.ExportJobCreateRequest;
import co.za.tveco.bff.dto.ExportJobDto;
import co.za.tveco.bff.service.ExportJobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}