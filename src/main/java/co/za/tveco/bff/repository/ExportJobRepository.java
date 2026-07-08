package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    Optional<ExportJob> findByPublicTrackingTokenIgnoreCase(String token);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(job_number FROM '.+-(\\d+)$') AS INT)), 0)
            FROM export_jobs
            WHERE job_number LIKE CONCAT('TVECO-EXP-', :year, '-%')
            """, nativeQuery = true)
    int findMaxSequenceForYear(String year);
}