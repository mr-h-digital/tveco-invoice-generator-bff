package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthSignupRequest;
import co.za.tveco.bff.dto.AuthTokens;
import co.za.tveco.bff.entity.AuthRecoveryOtpChallenge;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.Client;
import co.za.tveco.bff.entity.EmailOutboxMessage;
import co.za.tveco.bff.entity.PasswordResetToken;
import co.za.tveco.bff.entity.RefreshTokenSession;
import co.za.tveco.bff.service.messaging.email.EmailRecoveryProvider;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.UnauthorizedException;
import co.za.tveco.bff.repository.AuthRecoveryOtpChallengeRepository;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ClientRepository;
import co.za.tveco.bff.repository.EmailOutboxRepository;
import co.za.tveco.bff.repository.PasswordResetTokenRepository;
import co.za.tveco.bff.repository.RefreshTokenSessionRepository;
import co.za.tveco.bff.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final String INVALID_OR_EXPIRED_RECOVERY_MESSAGE = "Invalid or expired recovery request";

    private final AppUserRepository appUserRepository;
    private final ClientRepository clientRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthRecoveryOtpChallengeRepository authRecoveryOtpChallengeRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final RecoveryMessagingProvider messagingProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.password-reset-base-url:https://tveco.co.za/client-zone/#/reset-password}")
    private String passwordResetBaseUrl;

    public AuthService(
            AppUserRepository appUserRepository,
            ClientRepository clientRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            AuthRecoveryOtpChallengeRepository authRecoveryOtpChallengeRepository,
            EmailOutboxRepository emailOutboxRepository,
            RecoveryMessagingProvider messagingProvider,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.appUserRepository = appUserRepository;
        this.clientRepository = clientRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.authRecoveryOtpChallengeRepository = authRecoveryOtpChallengeRepository;
        this.emailOutboxRepository = emailOutboxRepository;
        this.messagingProvider = messagingProvider;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokens signup(AuthSignupRequest req) {
        String incomingEmail = req.email().trim().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByEmailIgnoreCase(incomingEmail)) {
            log.warn("Auth signup rejected: duplicate email={} ", incomingEmail);
            throw new ConflictException("An account with this email already exists");
        }

        Client client = clientRepository.findByEmailIgnoreCase(incomingEmail)
                .orElseGet(() -> clientRepository.save(Client.builder()
                        .companyName(req.companyName().trim())
                        .contactName(req.contactName().trim())
                        .email(incomingEmail)
                        .phone(req.phone().trim())
                        .address(req.address().trim())
                        .build()));

        AppUser user = appUserRepository.save(AppUser.builder()
                .email(incomingEmail)
                .passwordHash(passwordEncoder.encode(req.password()))
                .role("client")
                .clientId(client.getId())
                .active(true)
                .build());

        log.info("Auth signup success email={} role={} clientId={}", user.getEmail(), user.getRole(), user.getClientId());
        return issueTokens(user);
    }

    @Transactional
    public AuthTokens login(AuthLoginRequest req) {
        String incomingEmail = req.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = appUserRepository.findByEmailIgnoreCase(incomingEmail)
                .filter(AppUser::isActive)
                .orElseThrow(() -> {
                    log.warn("Auth login rejected: unknown/inactive email={}", incomingEmail);
                    return new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
                });

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            log.warn("Auth login rejected: invalid password email={}", incomingEmail);
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        log.info("Auth login success email={} role={} clientId={}", user.getEmail(), user.getRole(), user.getClientId());
        return issueTokens(user);
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        try {
            var claims = jwtService.parseRefreshToken(refreshToken.trim());
            String tokenId = claims.getId();
            String email = claims.getSubject();
            if (tokenId == null || email == null) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
            }

            RefreshTokenSession session = refreshTokenSessionRepository.findById(tokenId)
                    .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));

            if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
            }

            AppUser user = appUserRepository.findById(session.getUserId())
                    .filter(AppUser::isActive)
                    .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));

            if (!user.getEmail().trim().toLowerCase(Locale.ROOT).equals(email.trim().toLowerCase(Locale.ROOT))) {
                throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
            }

            session.setRevoked(true);
            session.setRevokedAt(Instant.now());
            refreshTokenSessionRepository.save(session);

            return issueTokens(user);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Auth refresh rejected: invalid refresh token");
            throw new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        try {
            var claims = jwtService.parseRefreshToken(refreshToken.trim());
            String tokenId = claims.getId();
            if (tokenId == null) {
                return;
            }

            refreshTokenSessionRepository.findById(tokenId).ifPresent(session -> {
                if (!session.isRevoked()) {
                    session.setRevoked(true);
                    session.setRevokedAt(Instant.now());
                    refreshTokenSessionRepository.save(session);
                }
            });
        } catch (JwtException | IllegalArgumentException ignored) {
            log.debug("Auth logout received invalid refresh token");
            // Logout should be idempotent, even when token is missing/invalid.
        }
    }

    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> maybeUser = appUserRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(AppUser::isActive);

        if (maybeUser.isEmpty()) {
            log.info("Forgot password requested for unknown/inactive email={}", normalizedEmail);
            return;
        }

        AppUser user = maybeUser.get();
        String rawToken = randomUrlToken(48);
        String tokenHash = sha256(rawToken);
        Instant expiry = Instant.now().plusSeconds(30 * 60);

        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .userId(user.getId())
                .expiresAt(expiry)
                .build());

        String resetLink = buildPasswordResetLink(rawToken);
        try {
            messagingProvider.sendPasswordResetEmail(user.getEmail(), resetLink, user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage(), e);
            // Don't propagate; let the token sit in DB and user can retry
        }
    }

    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        String tokenHash = sha256(token.trim());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException(INVALID_OR_EXPIRED_RECOVERY_MESSAGE));

        if (resetToken.getConsumedAt() != null || resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException(INVALID_OR_EXPIRED_RECOVERY_MESSAGE);
        }

        AppUser user = appUserRepository.findById(resetToken.getUserId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthorizedException(INVALID_OR_EXPIRED_RECOVERY_MESSAGE));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);

        resetToken.setConsumedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
        refreshTokenSessionRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public String requestOtpRecovery(String purpose, String channel, String identifier) {
        String normalizedPurpose = purpose.trim().toUpperCase(Locale.ROOT);
        String normalizedChannel = channel.trim().toUpperCase(Locale.ROOT);
        String normalizedIdentifier = normalizeIdentifier(normalizedChannel, identifier);

        AppUser resolvedUser = resolveUserForOtp(normalizedChannel, normalizedIdentifier).orElse(null);
        String otp = randomOtp();
        String challengeId = randomUrlToken(24);

        AuthRecoveryOtpChallenge challenge = AuthRecoveryOtpChallenge.builder()
                .challengeId(challengeId)
                .userId(resolvedUser != null ? resolvedUser.getId() : null)
                .purpose(normalizedPurpose)
                .channel(normalizedChannel)
                .identifier(normalizedIdentifier)
                .otpHash(sha256(otp))
                .attempts(0)
                .expiresAt(Instant.now().plusSeconds(10 * 60))
                .build();
        authRecoveryOtpChallengeRepository.save(challenge);

        if (resolvedUser != null) {
            queueOtpMessage(normalizedChannel, normalizedIdentifier, resolvedUser, otp, normalizedPurpose);
        }

        return challengeId;
    }

    @Transactional
    public OtpRecoveryResult verifyOtpRecovery(String challengeId, String otp, String newPassword) {
        AuthRecoveryOtpChallenge challenge = authRecoveryOtpChallengeRepository.findByChallengeId(challengeId.trim())
                .orElseThrow(() -> new UnauthorizedException(INVALID_OR_EXPIRED_RECOVERY_MESSAGE));

        if (challenge.getConsumedAt() != null || challenge.getExpiresAt().isBefore(Instant.now()) || challenge.getAttempts() >= 5) {
            throw new UnauthorizedException(INVALID_OR_EXPIRED_RECOVERY_MESSAGE);
        }

        String otpHash = sha256(otp.trim());
        if (!challenge.getOtpHash().equals(otpHash)) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            authRecoveryOtpChallengeRepository.save(challenge);
            throw new UnauthorizedException("Invalid OTP");
        }

        challenge.setConsumedAt(Instant.now());
        authRecoveryOtpChallengeRepository.save(challenge);

        String purpose = challenge.getPurpose();
        AppUser user = null;
        if (challenge.getUserId() != null) {
            user = appUserRepository.findById(challenge.getUserId()).orElse(null);
        }

        if ("USERNAME_RECOVERY".equals(purpose)) {
            return new OtpRecoveryResult(
                    user != null ? user.getEmail() : null,
                    false,
                    "Recovery complete."
            );
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("newPassword is required for PASSWORD_RESET");
        }

        if (user == null || !user.isActive()) {
            return new OtpRecoveryResult(null, false, "Recovery could not be completed.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
        refreshTokenSessionRepository.deleteByUserId(user.getId());

        return new OtpRecoveryResult(user.getEmail(), true, "Password reset successful.");
    }

    private AuthTokens issueTokens(AppUser user) {
        String refreshTokenId = UUID.randomUUID().toString();
        Instant refreshExpiry = Instant.now().plusSeconds(jwtService.getRefreshExpirationSeconds());

        refreshTokenSessionRepository.save(RefreshTokenSession.builder()
                .tokenId(refreshTokenId)
                .userId(user.getId())
                .expiresAt(refreshExpiry)
                .revoked(false)
                .build());

        return new AuthTokens(
                user.getEmail(),
                user.getRole(),
                user.getClientId(),
                jwtService.generateAccessToken(user.getEmail(), user.getRole()),
                jwtService.getAccessExpirationSeconds(),
                jwtService.generateRefreshToken(user.getEmail(), user.getRole(), refreshTokenId),
                jwtService.getRefreshExpirationSeconds()
        );
    }

    private void queueOtpMessage(String channel, String identifier, AppUser user, String otp, String purpose) {
        String destination;
        if ("EMAIL".equals(channel)) {
            destination = user.getEmail();
        } else {
            destination = findClientPhoneForUser(user).orElse(identifier);
        }

        try {
            messagingProvider.sendOtp(channel, destination, otp, purpose);
        } catch (Exception e) {
            log.error("Failed to send {} OTP to {}: {}", channel, destination, e.getMessage(), e);
            // Don't propagate; challenge is already created; user can retry
        }
    }

    private Optional<AppUser> resolveUserForOtp(String channel, String identifier) {
        if ("EMAIL".equals(channel)) {
            return appUserRepository.findByEmailIgnoreCase(identifier).filter(AppUser::isActive);
        }

        Optional<Client> directPhoneMatch = clientRepository.findByPhone(identifier);
        if (directPhoneMatch.isPresent()) {
            return appUserRepository.findByClientIdAndActiveTrue(directPhoneMatch.get().getId());
        }

        String normalizedPhone = digitsOnly(identifier);
        return clientRepository.findAll().stream()
                .filter(client -> digitsOnly(client.getPhone()).equals(normalizedPhone))
                .findFirst()
                .flatMap(client -> appUserRepository.findByClientIdAndActiveTrue(client.getId()));
    }

    private Optional<String> findClientPhoneForUser(AppUser user) {
        if (user.getClientId() == null) {
            return Optional.empty();
        }
        return clientRepository.findById(user.getClientId()).map(Client::getPhone);
    }

    private String normalizeIdentifier(String channel, String identifier) {
        String value = identifier.trim();
        if ("EMAIL".equals(channel)) {
            return value.toLowerCase(Locale.ROOT);
        }
        return value;
    }

    private String buildPasswordResetLink(String token) {
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String separator = passwordResetBaseUrl.contains("?") ? "&" : "?";
        return passwordResetBaseUrl + separator + "token=" + encoded;
    }

    private String randomUrlToken(int bytes) {
        byte[] data = new byte[bytes];
        secureRandom.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private String randomOtp() {
        int value = secureRandom.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public record OtpRecoveryResult(String username, boolean passwordReset, String message) {
    }
}
