package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.RefreshTokenSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, String> {
}
