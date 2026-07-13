package co.za.tveco.bff.dto;

public record OtpRecoveryVerifyResponse(
        String username,
        boolean passwordReset,
        String message
) {
}
