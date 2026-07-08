package co.za.tveco.bff.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExportJobDto(
        UUID id,
        String jobNumber,
        String publicTrackingToken,
        UUID clientId,
        JsonNode clientSnapshot,
        String destinationCountry,
        String vehicleDescription,
        String sourceChannel,
        BigDecimal projectValue,
        String status,
        JsonNode milestones,
        JsonNode documents,
        JsonNode paymentMilestones,
        JsonNode vaultDocuments,
        LocalDate estimatedDepartureDate,
        LocalDate estimatedArrivalDate,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}