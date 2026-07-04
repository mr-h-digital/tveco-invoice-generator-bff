package co.za.tveco.bff.dto;

public record PaymentDetailsDto(
        String bank,
        String accountName,
        String accountNumber,
        String accountType,
        String branchCode,
        String reference
) {}
