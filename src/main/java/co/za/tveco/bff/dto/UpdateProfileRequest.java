package co.za.tveco.bff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank
        @Email
        String email,

        String companyName,
        String contactName,
        String phone,
        String address
) {}
