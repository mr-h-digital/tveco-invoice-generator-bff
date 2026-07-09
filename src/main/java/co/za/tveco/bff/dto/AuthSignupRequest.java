package co.za.tveco.bff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthSignupRequest(
        @NotBlank String companyName,
        @NotBlank String contactName,
        @Email @NotBlank String email,
        @NotBlank String phone,
        @NotBlank String address,
        @NotBlank @Size(min = 8, max = 128) String password
) {}
