package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthLoginResponse;
import co.za.tveco.bff.dto.AuthSignupRequest;
import co.za.tveco.bff.dto.AuthTokens;
import co.za.tveco.bff.dto.ForgotPasswordRequest;
import co.za.tveco.bff.dto.OtpRecoveryChallengeResponse;
import co.za.tveco.bff.dto.OtpRecoveryRequest;
import co.za.tveco.bff.dto.OtpRecoveryVerifyRequest;
import co.za.tveco.bff.dto.OtpRecoveryVerifyResponse;
import co.za.tveco.bff.dto.ResetPasswordRequest;
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

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
        return ApiResponse.of(null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPasswordWithToken(req.token(), req.newPassword());
        return ApiResponse.of(null);
    }

    @PostMapping("/recovery/otp/request")
    public ApiResponse<OtpRecoveryChallengeResponse> requestOtpRecovery(@Valid @RequestBody OtpRecoveryRequest req) {
        String challengeId = authService.requestOtpRecovery(req.purpose(), req.channel(), req.identifier());
        return ApiResponse.of(new OtpRecoveryChallengeResponse(
                challengeId,
                "If your details match an account, an OTP has been sent."
        ));
    }

    @PostMapping("/recovery/otp/verify")
    public ApiResponse<OtpRecoveryVerifyResponse> verifyOtpRecovery(@Valid @RequestBody OtpRecoveryVerifyRequest req) {
        AuthService.OtpRecoveryResult result = authService.verifyOtpRecovery(req.challengeId(), req.otp(), req.newPassword());
        return ApiResponse.of(new OtpRecoveryVerifyResponse(
                result.username(),
                result.passwordReset(),
                result.message()
        ));
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
