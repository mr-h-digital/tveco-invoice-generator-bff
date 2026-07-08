package co.za.tveco.bff.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenRevocationService {

    private final ConcurrentHashMap<String, Instant> revokedTokenExpiries = new ConcurrentHashMap<>();

    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank() || expiresAt == null) {
            return;
        }
        cleanupExpired();
        revokedTokenExpiries.put(tokenId, expiresAt);
    }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return true;
        }

        Instant expiresAt = revokedTokenExpiries.get(tokenId);
        if (expiresAt == null) {
            return false;
        }

        if (expiresAt.isBefore(Instant.now())) {
            revokedTokenExpiries.remove(tokenId);
            return false;
        }

        return true;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> iterator = revokedTokenExpiries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue().isBefore(now)) {
                iterator.remove();
            }
        }
    }
}
