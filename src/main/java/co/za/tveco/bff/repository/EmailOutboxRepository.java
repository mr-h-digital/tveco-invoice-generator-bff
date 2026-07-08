package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.EmailOutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, UUID> {

    List<EmailOutboxMessage> findAllByOrderByCreatedAtDesc();

    List<EmailOutboxMessage> findByStatusInAndAttemptsLessThan(List<String> statuses, int maxAttempts);

    long countByStatus(String status);

    long deleteByStatus(String status);
}