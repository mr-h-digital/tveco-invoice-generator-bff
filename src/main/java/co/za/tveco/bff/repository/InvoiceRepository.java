package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findAll(Pageable pageable);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumberAndIdNot(String invoiceNumber, UUID id);

    // For next invoice number generation
    @Query("""
        SELECT COALESCE(MAX(CAST(SPLIT_PART(i.invoiceNumber, '-', 3) AS int)), 0)
        FROM Invoice i
        WHERE i.invoiceNumber LIKE CONCAT('TVECO-', :year, '-%')
        """)
    int findMaxSequenceForYear(@Param("year") String year);

    // Analytics queries
    @Query("""
        SELECT i FROM Invoice i
        WHERE i.issueDate BETWEEN :from AND :to
        ORDER BY i.issueDate ASC
        """)
    List<Invoice> findByIssueDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status")
    long countByStatus(@Param("status") String status);

    long countByExportJobId(UUID exportJobId);
}
