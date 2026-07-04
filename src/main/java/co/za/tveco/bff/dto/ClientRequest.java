package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientRequest(
        @NotBlank(message = "Company name is required")
        String companyName,

        @NotBlank(message = "Contact name is required")
        String contactName,

        String email,
        String phone,
        String address
) {}
