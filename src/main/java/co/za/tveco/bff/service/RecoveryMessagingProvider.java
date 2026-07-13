package co.za.tveco.bff.service;

/**
 * Interface for abstracting recovery messaging delivery (email, SMS, WhatsApp, etc.)
 * Allows pluggable provider implementations: Meta WhatsApp, Twilio, AWS SNS, etc.
 */
public interface RecoveryMessagingProvider {

    /**
     * Send a password reset email with the reset link.
     *
     * @param toEmail        The recipient email address
     * @param resetLink      The full reset link (e.g., https://tveco.co.za/client-zone/#/reset-password?token=...)
     * @param userName       The user's email/name for personalization (optional)
     * @throws IllegalArgumentException if email or resetLink is invalid
     * @throws RuntimeException if delivery fails
     */
    void sendPasswordResetEmail(String toEmail, String resetLink, String userName);

    /**
     * Send a one-time password (OTP) via the specified channel.
     *
     * @param channel        The delivery channel (EMAIL, SMS, WHATSAPP)
     * @param destination    Email address (for EMAIL) or phone number (for SMS/WHATSAPP)
     * @param otp            The 6-digit OTP code
     * @param purpose        The recovery purpose (USERNAME_RECOVERY or PASSWORD_RESET)
     * @throws IllegalArgumentException if channel, destination, or otp is invalid
     * @throws RuntimeException if delivery fails
     */
    void sendOtp(String channel, String destination, String otp, String purpose);
}
