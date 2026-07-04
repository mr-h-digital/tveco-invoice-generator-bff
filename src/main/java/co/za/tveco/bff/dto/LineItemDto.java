package co.za.tveco.bff.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LineItemDto(
        UUID id,
        String name,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        int sortOrder
) {}
