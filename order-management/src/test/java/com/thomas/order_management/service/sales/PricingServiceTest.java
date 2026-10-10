package com.thomas.order_management.service.sales;

import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.PricingCondition;
import com.thomas.order_management.model.Product;
import com.thomas.order_management.model.TaxCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PricingService – Kalkulationsschema")
class PricingServiceTest {

    private final PricingService pricing = new PricingService();
    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        customer = new Customer("Erika", "Muster", "erika@example.com");
        customer.setDiscountPercent(BigDecimal.ZERO);
        product = new Product("Bürostuhl", null, new BigDecimal("100.00"), 0);
        product.setTaxCode(TaxCode.FULL);
        product.setCostPrice(new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("PR00 und MWST ohne Rabatte")
    void shouldPriceWithoutDiscounts() {
        PricingService.PricingResult result = pricing.price(customer, product, 2, null);

        assertThat(result.netAmount()).isEqualByComparingTo("200.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("38.00");
        assertThat(result.grossAmount()).isEqualByComparingTo("238.00");
        assertThat(result.costAmount()).isEqualByComparingTo("120.00");
        assertThat(result.conditions()).extracting(PricingCondition::getConditionType)
                .containsExactly("PR00", "MWST", "VPRS");
    }

    @Test
    @DisplayName("K007 Kundenrabatt, ZMEN Mengenrabatt und RA01 Positionsrabatt werden kumuliert")
    void shouldApplyAllDiscounts() {
        customer.setDiscountPercent(new BigDecimal("10"));

        // 10 Stück → 3 % Mengenrabatt; RA01 bezieht sich auf den Wert nach K007/ZMEN
        PricingService.PricingResult result = pricing.price(customer, product, 10, new BigDecimal("5"));

        // 1000,00 − 100,00 (K007) − 30,00 (ZMEN) = 870,00; − 5 % = 43,50 → 826,50
        assertThat(result.netAmount()).isEqualByComparingTo("826.50");
        assertThat(result.taxAmount()).isEqualByComparingTo("157.04");
        assertThat(result.conditions()).extracting(PricingCondition::getConditionType)
                .containsExactly("PR00", "K007", "ZMEN", "RA01", "MWST", "VPRS");
        assertThat(result.conditions()).filteredOn(c -> c.getConditionType().equals("VPRS"))
                .allMatch(PricingCondition::isStatistical);
    }

    @Test
    @DisplayName("Ermäßigter Steuersatz für Steuerkennzeichen REDUCED")
    void shouldUseReducedTaxRate() {
        product.setTaxCode(TaxCode.REDUCED);

        PricingService.PricingResult result = pricing.price(customer, product, 1, null);

        assertThat(result.taxRate()).isEqualByComparingTo("7");
        assertThat(result.taxAmount()).isEqualByComparingTo("7.00");
    }

    @ParameterizedTest(name = "{0} Stück → {1} % Mengenrabatt")
    @CsvSource({"1, 0", "9, 0", "10, 3", "24, 3", "25, 5", "100, 5"})
    @DisplayName("Mengenstaffel ZMEN")
    void shouldApplyQuantityScale(int quantity, String percent) {
        assertThat(PricingService.quantityDiscountPercent(quantity)).isEqualByComparingTo(percent);
    }
}
