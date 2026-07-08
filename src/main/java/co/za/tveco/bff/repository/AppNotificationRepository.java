package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {

    List<AppNotification> findAllByOrderByCreatedAtDesc();

    long countByReadFalse();
}