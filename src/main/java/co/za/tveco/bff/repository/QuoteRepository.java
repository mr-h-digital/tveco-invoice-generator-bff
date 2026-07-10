package co.za.tveco.bff.repository;

import co.za.tveco.bff.entity.Quote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    Page<Quote> findAll(Pageable pageable);

    boolean existsByQuoteNumber(String quoteNumber);

    boolean existsByQuoteNumberAndIdNot(String quoteNumber, UUID id);

    List<Quote> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<Quote> findByInquiryIdOrderByCreatedAtDesc(UUID inquiryId);

    Optional<Quote> findByIdAndClientId(UUID id, UUID clientId);

    @Query("""
        SELECT COALESCE(MAX(CAST(SPLIT_PART(q.quoteNumber, '-', 3) AS int)), 0)
        FROM Quote q
        WHERE q.quoteNumber LIKE CONCAT('QUO-', :year, '-%')
        """)
    int findMaxSequenceForYear(@Param("year") String year);
}
