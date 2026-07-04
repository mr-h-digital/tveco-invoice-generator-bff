package co.za.tveco.bff.service;

import co.za.tveco.bff.dto.LineItemRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class InvoiceCalculator {

    public record Totals(
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal vatAmount,
            BigDecimal total
    ) {}

    public static Totals calculate(
            List<LineItemRequest> items,
            String discountType,
            BigDecimal discountValue,
            boolean vatEnabled,
            BigDecimal vatRate
    ) {
        BigDecimal subtotal = items.stream()
                .map(li -> li.unitPrice().multiply(li.quantity()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountAmount = BigDecimal.ZERO;
        if (discountValue != null && discountValue.compareTo(BigDecimal.ZERO) > 0) {
            if ("PERCENT".equals(discountType)) {
                discountAmount = subtotal.multiply(discountValue)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else if ("AMOUNT".equals(discountType)) {
                discountAmount = discountValue.setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal afterDiscount = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        BigDecimal vatAmount = BigDecimal.ZERO;
        if (vatEnabled && vatRate != null && vatRate.compareTo(BigDecimal.ZERO) > 0) {
            vatAmount = afterDiscount.multiply(vatRate).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = afterDiscount.add(vatAmount);

        return new Totals(subtotal, discountAmount, vatAmount, total);
    }

    public static BigDecimal lineAmount(BigDecimal unitPrice, BigDecimal quantity) {
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}
