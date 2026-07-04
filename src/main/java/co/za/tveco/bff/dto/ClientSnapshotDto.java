package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientSnapshotDto(
        @NotBlank(message = "Client company name is required")
        String companyName,

        String contactName,
        String email,
        String phone,
        String address
) {}
