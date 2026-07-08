package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthTokens;
import co.za.tveco.bff.exception.UnauthorizedException;
import co.za.tveco.bff.security.JwtService;
import co.za.tveco.bff.security.RefreshTokenRevocationService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final String adminEmail;
    private final String adminPassword;
    private final JwtService jwtService;
    private final RefreshTokenRevocationService refreshTokenRevocationService;

    public AuthService(
            @Value("${app.auth.admin-email:admin@tveco.co.za}") String adminEmail,
            @Value("${app.auth.admin-password:tveco2026}") String adminPassword,
            JwtService jwtService,
            RefreshTokenRevocationService refreshTokenRevocationService
    ) {
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
        this.jwtService = jwtService;
        this.refreshTokenRevocationService = refreshTokenRevocationService;
    }

    public AuthTokens login(AuthLoginRequest req) {
        String incomingEmail = req.email().trim().toLowerCase(Locale.ROOT);
        if (!adminEmail.equals(incomingEmail) || !adminPassword.equals(req.password())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return issueTokens(adminEmail, "admin");
    }

    public AuthTokens refresh(String refreshToken) {
        try {
            var claims = jwtService.parseRefreshToken(refreshToken.trim());
            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            String tokenId = claims.getId();
            Instant expiresAt = claims.getExpiration().toInstant();

            if (email == null || role == null || !adminEmail.equals(email.trim().toLowerCase(Locale.ROOT))) {
                throw new UnauthorizedException("Invalid refresh token");
            }

            if (refreshTokenRevocationService.isRevoked(tokenId)) {
                throw new UnauthorizedException("Invalid refresh token");
            }

            refreshTokenRevocationService.revoke(tokenId, expiresAt);

            return issueTokens(email, role);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }

    public void logout(String refreshToken) {
        try {
            var claims = jwtService.parseRefreshToken(refreshToken.trim());
            refreshTokenRevocationService.revoke(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException ignored) {
            // Logout should be idempotent, even when token is missing/invalid.
        }
    }

    private AuthTokens issueTokens(String email, String role) {
        return new AuthTokens(
                email,
                role,
                jwtService.generateAccessToken(email, role),
                jwtService.getAccessExpirationSeconds(),
                jwtService.generateRefreshToken(email, role),
                jwtService.getRefreshExpirationSeconds()
        );
    }
}
