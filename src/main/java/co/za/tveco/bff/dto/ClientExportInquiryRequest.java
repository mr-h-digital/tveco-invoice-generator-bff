package co.za.tveco.bff.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ClientExportInquiryRequest(
        @NotBlank String inquiryType,
        @NotBlank String destinationCountry,
        @NotBlank String vehicleDescription,
        @DecimalMin(value = "0.01", message = "projectValue must be greater than 0") BigDecimal projectValue,
        String estimatedDepartureDate,
        String estimatedArrivalDate,
        String notes
) {}
