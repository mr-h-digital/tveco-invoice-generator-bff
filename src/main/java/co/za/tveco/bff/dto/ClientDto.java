package co.za.tveco.bff.dto;

import java.time.Instant;
import java.util.UUID;

public record ClientDto(
        UUID id,
        String companyName,
        String contactName,
        String email,
        String phone,
        String address,
        Instant createdAt,
        Instant updatedAt
) {}
