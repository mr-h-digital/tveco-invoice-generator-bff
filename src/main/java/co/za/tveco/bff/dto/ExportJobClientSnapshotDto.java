package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record ExportJobClientSnapshotDto(
        @NotBlank String companyName,
        @NotBlank String contactName,
        @NotBlank String email,
        @NotBlank String phone
) {}