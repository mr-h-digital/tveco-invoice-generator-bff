package co.za.tveco.bff.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey signingKey;
    private final long accessExpirationSeconds;
    private final long refreshExpirationSeconds;

    public JwtService(
            @Value("${app.auth.jwt-secret}") String jwtSecret,
            @Value("${app.auth.jwt-expiration-seconds:43200}") long accessExpirationSeconds,
            @Value("${app.auth.jwt-refresh-expiration-seconds:1209600}") long refreshExpirationSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationSeconds = accessExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public String generateAccessToken(String email, String role) {
        return generateToken(email, role, accessExpirationSeconds, ACCESS_TOKEN_TYPE, null);
    }

    public String generateRefreshToken(String email, String role, String tokenId) {
        return generateToken(email, role, refreshExpirationSeconds, REFRESH_TOKEN_TYPE, tokenId);
    }

    private String generateToken(String email, String role, long expirationSeconds, String tokenType, String tokenId) {
        Instant now = Instant.now();
        String resolvedTokenId = (tokenId == null || tokenId.isBlank()) ? UUID.randomUUID().toString() : tokenId;
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .id(resolvedTokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAccessToken(String token) {
        return parseTokenWithType(token, ACCESS_TOKEN_TYPE);
    }

    public Claims parseRefreshToken(String token) {
        return parseTokenWithType(token, REFRESH_TOKEN_TYPE);
    }

    private Claims parseTokenWithType(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedType.equals(tokenType)) {
            throw new IllegalArgumentException("Invalid token type");
        }

        return claims;
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationSeconds;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationSeconds;
    }
}