package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthSignupRequest;
import co.za.tveco.bff.dto.AuthTokens;
import co.za.tveco.bff.entity.AppUser;
import co.za.tveco.bff.entity.Client;
import co.za.tveco.bff.entity.RefreshTokenSession;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.UnauthorizedException;
import co.za.tveco.bff.repository.AppUserRepository;
import co.za.tveco.bff.repository.ClientRepository;
import co.za.tveco.bff.repository.RefreshTokenSessionRepository;
import co.za.tveco.bff.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final AppUserRepository appUserRepository;
    private final ClientRepository clientRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository appUserRepository,
            ClientRepository clientRepository,
            RefreshTokenSessionRepository refreshTokenSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.appUserRepository = appUserRepository;
        this.clientRepository = clientRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthTokens signup(AuthSignupRequest req) {
        String incomingEmail = req.email().trim().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByEmailIgnoreCase(incomingEmail)) {
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

        return issueTokens(user);
    }

    @Transactional
    public AuthTokens login(AuthLoginRequest req) {
        String incomingEmail = req.email().trim().toLowerCase(Locale.ROOT);
        AppUser user = appUserRepository.findByEmailIgnoreCase(incomingEmail)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

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
            // Logout should be idempotent, even when token is missing/invalid.
        }
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
}
