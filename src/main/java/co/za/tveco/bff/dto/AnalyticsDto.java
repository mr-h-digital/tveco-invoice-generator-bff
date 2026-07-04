package co.za.tveco.bff.dto;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsDto(
        BigDecimal totalInvoiced,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        BigDecimal totalOverdue,
        int invoiceCount,
        int clientCount,
        int collectionRate,
        BigDecimal avgInvoiceValue,
        List<MonthRevenueDto> monthly,
        List<StatusSliceDto> statusBreakdown,
        List<ClientRevenueDto> topClients,
        List<ServiceRevenueDto> topServices
) {
    public record MonthRevenueDto(
            String month,
            BigDecimal invoiced,
            BigDecimal paid,
            BigDecimal outstanding,
            BigDecimal overdue
    ) {}

    public record StatusSliceDto(
            String name,
            BigDecimal value,
            int count,
            String color
    ) {}

    public record ClientRevenueDto(
            String name,
            BigDecimal total,
            BigDecimal paid,
            int invoices
    ) {}

    public record ServiceRevenueDto(
            String name,
            BigDecimal total,
            int count
    ) {}
}
