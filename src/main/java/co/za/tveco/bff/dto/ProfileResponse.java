package co.za.tveco.bff.dto;

public record ProfileResponse(
        String email,
        String role,
        String companyName,
        String contactName,
        String phone,
        String address
) {}
