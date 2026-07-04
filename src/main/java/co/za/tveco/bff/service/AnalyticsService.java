package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.AnalyticsDto;
import co.za.tveco.bff.dto.AnalyticsDto.*;
import co.za.tveco.bff.entity.Invoice;
import co.za.tveco.bff.repository.ClientRepository;
import co.za.tveco.bff.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository  clientRepository;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM yy");

    @Transactional(readOnly = true)
    public AnalyticsDto compute(LocalDate from, LocalDate to) {

        List<Invoice> invoices = invoiceRepository.findByIssueDateBetween(from, to);

        BigDecimal totalInvoiced    = BigDecimal.ZERO;
        BigDecimal totalPaid        = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue     = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            totalInvoiced = totalInvoiced.add(inv.getTotal());
            switch (inv.getStatus()) {
                case "PAID"    -> totalPaid        = totalPaid.add(inv.getTotal());
                case "SENT"    -> totalOutstanding = totalOutstanding.add(inv.getTotal());
                case "OVERDUE" -> totalOverdue     = totalOverdue.add(inv.getTotal());
            }
        }

        int clientCount = (int) clientRepository.count();
        int invoiceCount = invoices.size();

        // Collection rate
        BigDecimal nonDraftTotal = invoices.stream()
                .filter(i -> !"DRAFT".equals(i.getStatus()))
                .map(Invoice::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int collectionRate = 0;
        if (nonDraftTotal.compareTo(BigDecimal.ZERO) > 0) {
            collectionRate = totalPaid.multiply(BigDecimal.valueOf(100))
                    .divide(nonDraftTotal, 0, RoundingMode.HALF_UP)
                    .intValue();
        }

        BigDecimal avgInvoiceValue = invoiceCount > 0
                ? totalInvoiced.divide(BigDecimal.valueOf(invoiceCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<MonthRevenueDto>   monthly        = buildMonthly(invoices, from, to);
        List<StatusSliceDto>    statusBreakdown = buildStatusBreakdown(invoices);
        List<ClientRevenueDto>  topClients     = buildTopClients(invoices, 6);
        List<ServiceRevenueDto> topServices    = buildTopServices(invoices, 6);

        return new AnalyticsDto(
                totalInvoiced, totalPaid, totalOutstanding, totalOverdue,
                invoiceCount, clientCount, collectionRate, avgInvoiceValue,
                monthly, statusBreakdown, topClients, topServices
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<MonthRevenueDto> buildMonthly(List<Invoice> invoices, LocalDate from, LocalDate to) {
        // Seed every month in range so empty months appear
        LinkedHashMap<String, MonthBucket> map = new LinkedHashMap<>();
        YearMonth cursor = YearMonth.from(from);
        YearMonth end    = YearMonth.from(to);
        while (!cursor.isAfter(end)) {
            String key = cursor.atDay(1).format(MONTH_FMT);
            map.put(key, new MonthBucket());
            cursor = cursor.plusMonths(1);
        }

        for (Invoice inv : invoices) {
            String key = inv.getIssueDate().withDayOfMonth(1).format(MONTH_FMT);
            MonthBucket bucket = map.get(key);
            if (bucket == null) continue;
            bucket.invoiced = bucket.invoiced.add(inv.getTotal());
            switch (inv.getStatus()) {
                case "PAID"    -> bucket.paid        = bucket.paid.add(inv.getTotal());
                case "SENT"    -> bucket.outstanding = bucket.outstanding.add(inv.getTotal());
                case "OVERDUE" -> bucket.overdue     = bucket.overdue.add(inv.getTotal());
            }
        }

        return map.entrySet().stream()
                .map(e -> new MonthRevenueDto(
                        e.getKey(),
                        e.getValue().invoiced,
                        e.getValue().paid,
                        e.getValue().outstanding,
                        e.getValue().overdue
                ))
                .toList();
    }

    private List<StatusSliceDto> buildStatusBreakdown(List<Invoice> invoices) {
        record Row(BigDecimal value, int count, String color) {}
        Map<String, Row> map = new LinkedHashMap<>();
        map.put("PAID",    new Row(BigDecimal.ZERO, 0, "#22C55E"));
        map.put("SENT",    new Row(BigDecimal.ZERO, 0, "#60A5FA"));
        map.put("OVERDUE", new Row(BigDecimal.ZERO, 0, "#EF4444"));
        map.put("DRAFT",   new Row(BigDecimal.ZERO, 0, "#5A6A7A"));

        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Integer>    counts = new HashMap<>();
        for (Invoice inv : invoices) {
            totals.merge(inv.getStatus(), inv.getTotal(), BigDecimal::add);
            counts.merge(inv.getStatus(), 1, Integer::sum);
        }

        return map.entrySet().stream()
                .filter(e -> counts.getOrDefault(e.getKey(), 0) > 0)
                .map(e -> new StatusSliceDto(
                        e.getKey(),
                        totals.getOrDefault(e.getKey(), BigDecimal.ZERO),
                        counts.getOrDefault(e.getKey(), 0),
                        e.getValue().color()
                ))
                .toList();
    }

    private List<ClientRevenueDto> buildTopClients(List<Invoice> invoices, int limit) {
        record Acc(BigDecimal total, BigDecimal paid, int count) {}
        Map<String, Acc> map = new LinkedHashMap<>();

        for (Invoice inv : invoices) {
            String key = inv.getSnapCompanyName().isBlank() ? "Unknown" : inv.getSnapCompanyName();
            Acc current = map.getOrDefault(key, new Acc(BigDecimal.ZERO, BigDecimal.ZERO, 0));
            BigDecimal newPaid = "PAID".equals(inv.getStatus())
                    ? current.paid().add(inv.getTotal())
                    : current.paid();
            map.put(key, new Acc(current.total().add(inv.getTotal()), newPaid, current.count() + 1));
        }

        return map.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Acc> e) -> e.getValue().total()).reversed())
                .limit(limit)
                .map(e -> new ClientRevenueDto(e.getKey(), e.getValue().total(), e.getValue().paid(), e.getValue().count()))
                .toList();
    }

    private List<ServiceRevenueDto> buildTopServices(List<Invoice> invoices, int limit) {
        record Acc(BigDecimal total, int count) {}
        Map<String, Acc> map = new LinkedHashMap<>();

        for (Invoice inv : invoices) {
            inv.getLineItems().forEach(li -> {
                String key = li.getName().isBlank() ? "Unnamed" : li.getName();
                Acc current = map.getOrDefault(key, new Acc(BigDecimal.ZERO, 0));
                map.put(key, new Acc(current.total().add(li.getAmount()), current.count() + 1));
            });
        }

        return map.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Acc> e) -> e.getValue().total()).reversed())
                .limit(limit)
                .map(e -> new ServiceRevenueDto(e.getKey(), e.getValue().total(), e.getValue().count()))
                .toList();
    }

    // Mutable bucket for monthly aggregation
    private static class MonthBucket {
        BigDecimal invoiced    = BigDecimal.ZERO;
        BigDecimal paid        = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue     = BigDecimal.ZERO;
    }
}
