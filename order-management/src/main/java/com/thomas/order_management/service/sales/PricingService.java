package com.thomas.order_management.service.sales;

import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.PricingCondition;
import com.thomas.order_management.model.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Preisfindung nach einem vereinfachten Kalkulationsschema (angelehnt an RVAA01):
 * <pre>
 * 10 PR00 Preis                 (Listenpreis × Menge)
 * 20 K007 Kundenrabatt          (% aus dem Debitorstamm)
 * 30 ZMEN Mengenrabatt          (3 % ab 10 Stück, 5 % ab 25 Stück)
 * 40 RA01 Positionsrabatt       (manuell, %)
 *    = Nettowert
 * 50 MWST Ausgangssteuer        (Steuerkennzeichen des Materials)
 * 60 VPRS Verrechnungspreis     (statistisch, Einstandspreis × Menge)
 * </pre>
 */
@Service
public class PricingService {

    static final int QUANTITY_SCALE_TIER_1 = 10;
    static final int QUANTITY_SCALE_TIER_2 = 25;
    static final BigDecimal QUANTITY_DISCOUNT_TIER_1 = new BigDecimal("3.00");
    static final BigDecimal QUANTITY_DISCOUNT_TIER_2 = new BigDecimal("5.00");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public PricingResult price(Customer customer, Product product, int quantity, BigDecimal manualDiscountPercent) {
        List<PricingCondition> conditions = new ArrayList<>();
        BigDecimal qty = BigDecimal.valueOf(quantity);

        BigDecimal unitPrice = product.getPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal grossValue = money(unitPrice.multiply(qty));
        conditions.add(new PricingCondition(10, "PR00", "Preis", unitPrice, "EUR", grossValue, false));

        BigDecimal net = grossValue;

        BigDecimal customerDiscount = customer.getDiscountPercent();
        if (isPositive(customerDiscount)) {
            BigDecimal value = percentOf(grossValue, customerDiscount).negate();
            conditions.add(new PricingCondition(20, "K007", "Kundenrabatt", customerDiscount.negate(), "%", value, false));
            net = net.add(value);
        }

        BigDecimal quantityDiscount = quantityDiscountPercent(quantity);
        if (isPositive(quantityDiscount)) {
            BigDecimal value = percentOf(grossValue, quantityDiscount).negate();
            conditions.add(new PricingCondition(30, "ZMEN", "Mengenrabatt", quantityDiscount.negate(), "%", value, false));
            net = net.add(value);
        }

        if (isPositive(manualDiscountPercent)) {
            BigDecimal value = percentOf(net, manualDiscountPercent).negate();
            conditions.add(new PricingCondition(40, "RA01", "Positionsrabatt", manualDiscountPercent.negate(), "%", value, false));
            net = net.add(value);
        }

        BigDecimal taxRate = product.getTaxCode().getRate();
        BigDecimal tax = percentOf(net, taxRate);
        conditions.add(new PricingCondition(50, "MWST", "Ausgangssteuer", taxRate, "%", tax, false));

        BigDecimal cost = BigDecimal.ZERO;
        if (product.getCostPrice() != null) {
            cost = money(product.getCostPrice().multiply(qty));
            conditions.add(new PricingCondition(60, "VPRS", "Verrechnungspreis", product.getCostPrice(), "EUR", cost, true));
        }

        return new PricingResult(unitPrice, conditions, net, taxRate, tax, net.add(tax), cost);
    }

    static BigDecimal quantityDiscountPercent(int quantity) {
        if (quantity >= QUANTITY_SCALE_TIER_2) return QUANTITY_DISCOUNT_TIER_2;
        if (quantity >= QUANTITY_SCALE_TIER_1) return QUANTITY_DISCOUNT_TIER_1;
        return BigDecimal.ZERO;
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return money(base.multiply(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record PricingResult(
            BigDecimal unitPrice,
            List<PricingCondition> conditions,
            BigDecimal netAmount,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal grossAmount,
            BigDecimal costAmount
    ) {
    }
}
