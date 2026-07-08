package co.za.tveco.bff.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Service
public class RefreshTokenCookieService {

    private final String cookieName;
    private final String cookiePath;
    private final String sameSite;
    private final boolean secureCookie;

    public RefreshTokenCookieService(
            @Value("${app.auth.refresh-cookie-name:TVECO_REFRESH_TOKEN}") String cookieName,
            @Value("${app.auth.refresh-cookie-path:/api/auth}") String cookiePath,
            @Value("${app.auth.refresh-cookie-same-site:Lax}") String sameSite,
            @Value("${app.auth.refresh-cookie-secure:true}") boolean secureCookie
    ) {
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.sameSite = sameSite;
        this.secureCookie = secureCookie;
    }

    public Optional<String> resolveRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
