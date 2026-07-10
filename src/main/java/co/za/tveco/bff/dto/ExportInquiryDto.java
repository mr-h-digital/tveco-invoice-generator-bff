package co.za.tveco.bff.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExportInquiryDto(
        UUID id,
        String inquiryNumber,
        UUID clientId,
        String inquiryType,
        String status,
        String sourceChannel,
        String destinationCountry,
        String vehicleDescription,
        BigDecimal projectValue,
        LocalDate estimatedDepartureDate,
        LocalDate estimatedArrivalDate,
        String notes,
        List<ExportInquiryMessageDto> messages,
        UUID linkedQuoteId,
        String linkedQuoteNumber,
        String linkedQuoteStatus,
        UUID linkedExportJobId,
        String linkedExportJobNumber,
        Instant createdAt,
        Instant updatedAt
) {}
