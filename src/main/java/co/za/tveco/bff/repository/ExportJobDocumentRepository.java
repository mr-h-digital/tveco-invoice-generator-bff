package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.ExportJobDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobDocumentRepository extends JpaRepository<ExportJobDocument, UUID> {

    List<ExportJobDocument> findByExportJobIdOrderByCreatedAtDesc(UUID exportJobId);

    Optional<ExportJobDocument> findByIdAndExportJobId(UUID id, UUID exportJobId);
}