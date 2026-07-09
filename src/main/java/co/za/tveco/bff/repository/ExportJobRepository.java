package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    Optional<ExportJob> findByPublicTrackingTokenIgnoreCase(String token);

    List<ExportJob> findByJobNumberStartingWith(String prefix);

    List<ExportJob> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    Optional<ExportJob> findByIdAndClientId(UUID id, UUID clientId);
}