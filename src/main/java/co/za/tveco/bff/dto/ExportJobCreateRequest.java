package co.za.tveco.bff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record ExportJobCreateRequest(
        UUID clientId,

        @NotNull @Valid
        ExportJobClientSnapshotDto clientSnapshot,

        @NotBlank
        String destinationCountry,

        @NotBlank
        String vehicleDescription,

        @NotBlank
        @Pattern(regexp = "Website|WhatsApp|Referral|Direct", message = "sourceChannel must be Website, WhatsApp, Referral or Direct")
        String sourceChannel,

        @NotNull
        @DecimalMin(value = "0.01", message = "projectValue must be greater than 0")
        BigDecimal projectValue,

        String estimatedDepartureDate,
        String estimatedArrivalDate,
        String notes
) {}