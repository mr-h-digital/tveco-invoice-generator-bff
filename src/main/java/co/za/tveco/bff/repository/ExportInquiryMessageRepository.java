package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.ExportInquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExportInquiryMessageRepository extends JpaRepository<ExportInquiryMessage, UUID> {

    List<ExportInquiryMessage> findByInquiryIdOrderByCreatedAtAsc(UUID inquiryId);
}
