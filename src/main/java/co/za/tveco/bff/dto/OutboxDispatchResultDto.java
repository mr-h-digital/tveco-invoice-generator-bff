package co.za.tveco.bff.dto;

public record OutboxDispatchResultDto(
        int sent,
        int failed,
        boolean skipped
) {}