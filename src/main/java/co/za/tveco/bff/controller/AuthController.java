package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthLoginResponse;
import co.za.tveco.bff.dto.AuthSignupRequest;
import co.za.tveco.bff.dto.AuthTokens;
import co.za.tveco.bff.exception.UnauthorizedException;
import co.za.tveco.bff.security.AuthRateLimiter;
import co.za.tveco.bff.security.RefreshTokenCookieService;
import co.za.tveco.bff.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final AuthRateLimiter authRateLimiter;

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(
            @Valid @RequestBody AuthLoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authRateLimiter.assertLoginAllowed(resolveClientIp(request), req.email());
        AuthTokens tokens = authService.login(req);
        refreshTokenCookieService.writeRefreshTokenCookie(response, tokens.refreshToken(), tokens.refreshExpiresInSeconds());
        return ApiResponse.of(new AuthLoginResponse(
                tokens.email(),
                tokens.role(),
                tokens.clientId(),
                tokens.accessToken(),
                tokens.expiresInSeconds()
        ));
    }

    @PostMapping("/signup")
    public ApiResponse<AuthLoginResponse> signup(
            @Valid @RequestBody AuthSignupRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        authRateLimiter.assertSignupAllowed(resolveClientIp(request), req.email());
        AuthTokens tokens = authService.signup(req);
        refreshTokenCookieService.writeRefreshTokenCookie(response, tokens.refreshToken(), tokens.refreshExpiresInSeconds());
        return ApiResponse.of(new AuthLoginResponse(
                tokens.email(),
                tokens.role(),
                tokens.clientId(),
                tokens.accessToken(),
                tokens.expiresInSeconds()
        ));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthLoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = refreshTokenCookieService.resolveRefreshToken(request)
                .orElseThrow(() -> new UnauthorizedException("Missing refresh token"));

        AuthTokens tokens = authService.refresh(refreshToken);
        refreshTokenCookieService.writeRefreshTokenCookie(response, tokens.refreshToken(), tokens.refreshExpiresInSeconds());

        return ApiResponse.of(new AuthLoginResponse(
                tokens.email(),
                tokens.role(),
            tokens.clientId(),
                tokens.accessToken(),
                tokens.expiresInSeconds()
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenCookieService.resolveRefreshToken(request).ifPresent(authService::logout);
        refreshTokenCookieService.clearRefreshTokenCookie(response);
        return ApiResponse.of(null);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
    }
}
