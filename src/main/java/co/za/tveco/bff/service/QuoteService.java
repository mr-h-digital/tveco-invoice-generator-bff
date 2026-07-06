package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.ClientSnapshotDto;
import co.za.tveco.bff.dto.LineItemDto;
import co.za.tveco.bff.dto.LineItemRequest;
import co.za.tveco.bff.dto.QuoteDto;
import co.za.tveco.bff.dto.QuoteRequest;
import co.za.tveco.bff.entity.Quote;
import co.za.tveco.bff.entity.QuoteLineItem;
import co.za.tveco.bff.exception.ConflictException;
import co.za.tveco.bff.exception.ResourceNotFoundException;
import co.za.tveco.bff.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;

    @Transactional(readOnly = true)
    public Page<QuoteDto> getAll(Pageable pageable) {
        return quoteRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public QuoteDto getById(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public String getNextQuoteNumber() {
        String year = String.valueOf(LocalDate.now().getYear());
        int max = quoteRepository.findMaxSequenceForYear(year);
        return String.format("QUO-%s-%03d", year, max + 1);
    }

    @Transactional
    public QuoteDto create(QuoteRequest req) {
        if (quoteRepository.existsByQuoteNumber(req.quoteNumber())) {
            throw new ConflictException("Quote number '" + req.quoteNumber() + "' already exists");
        }
        Quote quote = buildQuote(new Quote(), req);
        return toDto(quoteRepository.save(quote));
    }

    @Transactional
    public QuoteDto update(UUID id, QuoteRequest req) {
        Quote quote = findOrThrow(id);
        if (quoteRepository.existsByQuoteNumberAndIdNot(req.quoteNumber(), id)) {
            throw new ConflictException("Quote number '" + req.quoteNumber() + "' is already used by another quote");
        }
        quote.getLineItems().clear();
        buildQuote(quote, req);
        return toDto(quoteRepository.save(quote));
    }

    @Transactional
    public QuoteDto updateStatus(UUID id, String status) {
        Quote quote = findOrThrow(id);
        quote.setStatus(status);
        return toDto(quoteRepository.save(quote));
    }

    @Transactional
    public void delete(UUID id) {
        if (!quoteRepository.existsById(id)) {
            throw new ResourceNotFoundException("Quote not found: " + id);
        }
        quoteRepository.deleteById(id);
    }

    @Transactional
    public QuoteDto duplicate(UUID id) {
        Quote original = findOrThrow(id);
        String newNumber = getNextQuoteNumber();

        Quote copy = Quote.builder()
                .quoteNumber(newNumber)
                .status("DRAFT")
                .issueDate(LocalDate.now())
                .expiryDate(original.getExpiryDate())
                .clientId(original.getClientId())
                .snapCompanyName(original.getSnapCompanyName())
                .snapContactName(original.getSnapContactName())
                .snapEmail(original.getSnapEmail())
                .snapPhone(original.getSnapPhone())
                .snapAddress(original.getSnapAddress())
                .subtotal(original.getSubtotal())
                .discountType(original.getDiscountType())
                .discountValue(original.getDiscountValue())
                .discountAmount(original.getDiscountAmount())
                .vatEnabled(original.isVatEnabled())
                .vatRate(original.getVatRate())
                .vatAmount(original.getVatAmount())
                .total(original.getTotal())
                .notes(original.getNotes())
                .build();

        original.getLineItems().forEach(li -> {
            QuoteLineItem copiedItem = QuoteLineItem.builder()
                    .quote(copy)
                    .name(li.getName())
                    .description(li.getDescription())
                    .quantity(li.getQuantity())
                    .unitPrice(li.getUnitPrice())
                    .amount(li.getAmount())
                    .sortOrder(li.getSortOrder())
                    .build();
            copy.getLineItems().add(copiedItem);
        });

        return toDto(quoteRepository.save(copy));
    }

    private Quote buildQuote(Quote quote, QuoteRequest req) {
        InvoiceCalculator.Totals totals = InvoiceCalculator.calculate(
                req.lineItems(),
                req.discountType(),
                req.discountValue() == null ? BigDecimal.ZERO : req.discountValue(),
                req.vatEnabled(),
                req.vatRate() == null ? BigDecimal.valueOf(0.15) : req.vatRate()
        );

        quote.setQuoteNumber(req.quoteNumber());
        quote.setStatus(req.status());
        quote.setIssueDate(LocalDate.parse(req.issueDate()));
        quote.setExpiryDate(LocalDate.parse(req.expiryDate()));
        quote.setClientId(req.clientId());

        ClientSnapshotDto snap = req.clientSnapshot();
        quote.setSnapCompanyName(nvl(snap.companyName()));
        quote.setSnapContactName(nvl(snap.contactName()));
        quote.setSnapEmail(nvl(snap.email()));
        quote.setSnapPhone(nvl(snap.phone()));
        quote.setSnapAddress(nvl(snap.address()));

        quote.setSubtotal(totals.subtotal());
        quote.setDiscountType(req.discountType());
        quote.setDiscountValue(req.discountValue() == null ? BigDecimal.ZERO : req.discountValue());
        quote.setDiscountAmount(totals.discountAmount());
        quote.setVatEnabled(req.vatEnabled());
        quote.setVatRate(req.vatRate() == null ? BigDecimal.valueOf(0.15) : req.vatRate());
        quote.setVatAmount(totals.vatAmount());
        quote.setTotal(totals.total());
        quote.setNotes(nvl(req.notes()));

        for (int i = 0; i < req.lineItems().size(); i++) {
            LineItemRequest li = req.lineItems().get(i);
            QuoteLineItem item = QuoteLineItem.builder()
                    .quote(quote)
                    .name(li.name())
                    .description(nvl(li.description()))
                    .quantity(li.quantity())
                    .unitPrice(li.unitPrice())
                    .amount(InvoiceCalculator.lineAmount(li.unitPrice(), li.quantity()))
                    .sortOrder(li.sortOrder() == 0 ? i : li.sortOrder())
                    .build();
            quote.getLineItems().add(item);
        }

        return quote;
    }

    private Quote findOrThrow(UUID id) {
        return quoteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found: " + id));
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    QuoteDto toDto(Quote quote) {
        List<LineItemDto> items = quote.getLineItems().stream()
                .map(li -> new LineItemDto(
                        li.getId(),
                        li.getName(),
                        li.getDescription(),
                        li.getQuantity(),
                        li.getUnitPrice(),
                        li.getAmount(),
                        li.getSortOrder()
                ))
                .toList();

        return new QuoteDto(
                quote.getId(),
                quote.getQuoteNumber(),
                quote.getStatus(),
                quote.getIssueDate(),
                quote.getExpiryDate(),
                quote.getClientId(),
                new ClientSnapshotDto(
                        quote.getSnapCompanyName(),
                        quote.getSnapContactName(),
                        quote.getSnapEmail(),
                        quote.getSnapPhone(),
                        quote.getSnapAddress()
                ),
                items,
                quote.getSubtotal(),
                quote.getDiscountType(),
                quote.getDiscountValue(),
                quote.getDiscountAmount(),
                quote.isVatEnabled(),
                quote.getVatRate(),
                quote.getVatAmount(),
                quote.getTotal(),
                quote.getNotes(),
                quote.getCreatedAt(),
                quote.getUpdatedAt()
        );
    }
}
