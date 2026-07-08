package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthLoginResponse;
import co.za.tveco.bff.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final String adminEmail;
    private final String adminPassword;

    public AuthService(
            @Value("${app.auth.admin-email:admin@tveco.co.za}") String adminEmail,
            @Value("${app.auth.admin-password:tveco2026}") String adminPassword
    ) {
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
    }

    public AuthLoginResponse login(AuthLoginRequest req) {
        String incomingEmail = req.email().trim().toLowerCase(Locale.ROOT);
        if (!adminEmail.equals(incomingEmail) || !adminPassword.equals(req.password())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        return new AuthLoginResponse(adminEmail, "admin");
    }
}
