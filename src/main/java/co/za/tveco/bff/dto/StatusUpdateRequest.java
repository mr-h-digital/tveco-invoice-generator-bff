package co.za.tveco.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StatusUpdateRequest(
        @NotBlank
        @Pattern(regexp = "DRAFT|SENT|PAID|OVERDUE", message = "Status must be DRAFT, SENT, PAID or OVERDUE")
        String status
) {}
