package co.za.tveco.bff.dto;

public record DocumentUploadCompleteRequest(
        String checksumSha256
) {}