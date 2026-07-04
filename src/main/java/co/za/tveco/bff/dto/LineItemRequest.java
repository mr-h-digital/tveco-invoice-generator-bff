package co.za.tveco.bff.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LineItemRequest(
        @NotBlank(message = "Line item name is required")
        String name,

        String description,

        @NotNull
        @DecimalMin(value = "0.0001", message = "Quantity must be greater than 0")
        BigDecimal quantity,

        @NotNull
        @DecimalMin(value = "0", inclusive = true, message = "Unit price must be 0 or more")
        BigDecimal unitPrice,

        int sortOrder
) {}
