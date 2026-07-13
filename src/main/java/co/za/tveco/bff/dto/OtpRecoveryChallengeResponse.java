package co.za.tveco.bff.dto;

public record OtpRecoveryChallengeResponse(
        String challengeId,
        String message
) {
}
