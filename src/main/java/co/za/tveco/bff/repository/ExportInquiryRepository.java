package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.ExportInquiry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExportInquiryRepository extends JpaRepository<ExportInquiry, UUID> {

    List<ExportInquiry> findByInquiryNumberStartingWith(String prefix);

    List<ExportInquiry> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
