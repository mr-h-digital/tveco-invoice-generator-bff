package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthLoginResponse;
import co.za.tveco.bff.dto.AuthRefreshRequest;
import co.za.tveco.bff.exception.UnauthorizedException;
import co.za.tveco.bff.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final String adminEmail;
    private final String adminPassword;
    private final JwtService jwtService;

    public AuthService(
            @Value("${app.auth.admin-email:admin@tveco.co.za}") String adminEmail,
            @Value("${app.auth.admin-password:tveco2026}") String adminPassword,
            JwtService jwtService
    ) {
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
        this.jwtService = jwtService;
    }

    public AuthLoginResponse login(AuthLoginRequest req) {
        String incomingEmail = req.email().trim().toLowerCase(Locale.ROOT);
        if (!adminEmail.equals(incomingEmail) || !adminPassword.equals(req.password())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return issueTokens(adminEmail, "admin");
    }

    public AuthLoginResponse refresh(AuthRefreshRequest req) {
        try {
            var claims = jwtService.parseRefreshToken(req.refreshToken().trim());
            String email = claims.getSubject();
            String role = claims.get("role", String.class);

            if (email == null || role == null || !adminEmail.equals(email.trim().toLowerCase(Locale.ROOT))) {
                throw new UnauthorizedException("Invalid refresh token");
            }

            return issueTokens(email, role);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }

    private AuthLoginResponse issueTokens(String email, String role) {
        return new AuthLoginResponse(
                email,
                role,
                jwtService.generateAccessToken(email, role),
                jwtService.getAccessExpirationSeconds(),
                jwtService.generateRefreshToken(email, role),
                jwtService.getRefreshExpirationSeconds()
        );
    }
}
