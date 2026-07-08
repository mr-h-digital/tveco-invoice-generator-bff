package co.za.tveco.bff.dto;

public record OutboxStatsDto(
        long pending,
        long failed,
        long sent
) {}