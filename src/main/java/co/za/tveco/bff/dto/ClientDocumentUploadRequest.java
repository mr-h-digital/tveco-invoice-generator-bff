package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClientDocumentUploadRequest(
        @NotBlank String name,
        @NotBlank String mimeType,
        @NotNull @Positive Long sizeBytes,
        @NotBlank String category,
        String dataUrl,
        String fileUrl
) {}
