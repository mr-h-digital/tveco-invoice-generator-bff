package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.AuthRecoveryOtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthRecoveryOtpChallengeRepository extends JpaRepository<AuthRecoveryOtpChallenge, UUID> {
    Optional<AuthRecoveryOtpChallenge> findByChallengeId(String challengeId);
}
