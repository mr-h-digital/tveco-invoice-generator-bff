package co.za.tveco.bff.service.messaging.email;

import co.za.tveco.bff.entity.EmailOutboxMessage;
import co.za.tveco.bff.repository.EmailOutboxRepository;
import co.za.tveco.bff.service.RecoveryMessagingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementation of RecoveryMessagingProvider using email via EmailOutbox system.
 *
 * This provider queues emails for asynchronous delivery through the existing
 * EmailOutboxMessage system, allowing for retry logic and improved reliability.
 */
@Service
@ConditionalOnProperty(
        name = "app.recovery.messaging.provider",
        havingValue = "email",
        matchIfMissing = true  // Default provider if not specified
)
public class EmailRecoveryProvider implements RecoveryMessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailRecoveryProvider.class);

    private final EmailOutboxRepository emailOutboxRepository;

    public EmailRecoveryProvider(EmailOutboxRepository emailOutboxRepository) {
        this.emailOutboxRepository = emailOutboxRepository;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink, String userName) {
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Email address is required");
        }

        String subject = "TVECO Password Reset";
        String body = "We received a password reset request for your TVECO account.\n\n"
                + "Use this secure link to set a new password:\n"
                + resetLink
                + "\n\nThis link expires in 30 minutes. If you did not request this, you can safely ignore this email.";

        emailOutboxRepository.save(EmailOutboxMessage.builder()
                .recipient(toEmail)
                .subject(subject)
                .body(body)
                .status("PENDING")
                .attempts(0)
                .build());

        log.debug("Password reset email queued for: {}", toEmail);
    }

    @Override
    public void sendOtp(String channel, String destination, String otp, String purpose) {
        if (!"EMAIL".equalsIgnoreCase(channel)) {
            throw new IllegalArgumentException("EmailRecoveryProvider only supports EMAIL channel");
        }

        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Email address is required");
        }

        if (otp == null || !otp.matches("\\d{6}")) {
            throw new IllegalArgumentException("OTP must be a 6-digit code");
        }

        String subject = "TVECO " + ("USERNAME_RECOVERY".equals(purpose) ? "Username Recovery OTP" : "Password Reset OTP");
        String body = "Use this one-time code to continue your account recovery: " + otp + "\n\n"
                + "This code expires in 10 minutes. Do not share this code with anyone.";

        emailOutboxRepository.save(EmailOutboxMessage.builder()
                .recipient(destination)
                .subject(subject)
                .body(body)
                .status("PENDING")
                .attempts(0)
                .build());

        log.debug("OTP email queued for: {}", destination);
    }
}
