package co.za.tveco.bff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthSignupRequest(
        @NotBlank(message = "Company name is required")
        @Size(max = 120, message = "Company name must be 120 characters or fewer")
        String companyName,

        @NotBlank(message = "Contact name is required")
        @Size(max = 120, message = "Contact name must be 120 characters or fewer")
        String contactName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 160, message = "Email must be 160 characters or fewer")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[+0-9()\\-\\s]{7,20}$", message = "Phone number format is invalid")
        String phone,

        @NotBlank(message = "Address is required")
        @Size(max = 240, message = "Address must be 240 characters or fewer")
        String address,

        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 128, message = "Password must be between 10 and 128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "Password must include uppercase, lowercase, number, and symbol"
        )
        String password
) {}
