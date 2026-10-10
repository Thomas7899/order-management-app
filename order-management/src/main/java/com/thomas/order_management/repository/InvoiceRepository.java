package com.thomas.order_management.repository;

import com.thomas.order_management.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Stornorechnungen (S1) werden mit negativen Beträgen gespeichert, daher ergeben
 * Summen über alle Fakturen direkt den Nettoumsatz nach Stornos.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<Invoice> findByDeliveryId(Long deliveryId);

    List<Invoice> findByCustomerIdOrderByBillingDateDesc(Long customerId);

    @Query("SELECT i FROM Invoice i WHERE i.status = com.thomas.order_management.model.Invoice.Status.OPEN AND i.billingType = com.thomas.order_management.model.Invoice.BillingType.F2")
    List<Invoice> findOpenItems();

    @Query("SELECT i FROM Invoice i WHERE i.customer.id = :customerId AND i.status = com.thomas.order_management.model.Invoice.Status.OPEN AND i.billingType = com.thomas.order_management.model.Invoice.BillingType.F2")
    List<Invoice> findOpenItemsByCustomer(@Param("customerId") Long customerId);

    @Query("SELECT COALESCE(SUM(i.netAmount), 0) FROM Invoice i WHERE i.billingDate BETWEEN :from AND :to")
    BigDecimal sumNetRevenue(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(i.grossAmount), 0) FROM Invoice i WHERE i.billingDate BETWEEN :from AND :to")
    BigDecimal sumGrossRevenue(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(DISTINCT i.customer.id) FROM Invoice i WHERE i.billingType = com.thomas.order_management.model.Invoice.BillingType.F2")
    long countBilledCustomersTotal();

    @Query("SELECT COUNT(DISTINCT i.order.id) FROM Invoice i WHERE i.billingDate BETWEEN :from AND :to AND i.billingType = com.thomas.order_management.model.Invoice.BillingType.F2 AND i.status <> com.thomas.order_management.model.Invoice.Status.CANCELLED")
    long countBilledOrders(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(i.costAmount), 0) FROM Invoice i WHERE i.billingDate BETWEEN :from AND :to")
    BigDecimal sumCost(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(i.netAmount), 0) FROM Invoice i")
    BigDecimal sumNetRevenueTotal();

    @Query("SELECT COUNT(DISTINCT i.customer.id) FROM Invoice i WHERE i.billingDate BETWEEN :from AND :to AND i.billingType = com.thomas.order_management.model.Invoice.BillingType.F2")
    long countBilledCustomers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.billingDate BETWEEN :from AND :to AND i.billingType = com.thomas.order_management.model.Invoice.BillingType.F2 AND i.status <> com.thomas.order_management.model.Invoice.Status.CANCELLED")
    long countInvoices(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = """
        SELECT EXTRACT(YEAR FROM billing_date) AS year, EXTRACT(MONTH FROM billing_date) AS month,
               COALESCE(SUM(net_amount), 0) AS revenue,
               COUNT(DISTINCT CASE WHEN billing_type = 'F2' THEN order_id END) AS order_count,
               COALESCE(SUM(cost_amount), 0) AS cost
        FROM invoices
        WHERE billing_date >= :startDate
        GROUP BY EXTRACT(YEAR FROM billing_date), EXTRACT(MONTH FROM billing_date)
        ORDER BY year, month
        """, nativeQuery = true)
    List<Object[]> getMonthlyRevenue(@Param("startDate") LocalDate startDate);

    @Query(value = """
        SELECT c.id, c.customer_number, c.first_name, c.last_name,
               COALESCE(SUM(i.net_amount), 0) AS revenue,
               COUNT(DISTINCT CASE WHEN i.billing_type = 'F2' THEN i.order_id END) AS order_count
        FROM customers c
        JOIN invoices i ON i.customer_id = c.id
        GROUP BY c.id, c.customer_number, c.first_name, c.last_name
        HAVING COALESCE(SUM(i.net_amount), 0) > 0
        ORDER BY revenue DESC
        """, nativeQuery = true)
    List<Object[]> getCustomerRevenue();

    @Query(value = """
        SELECT p.id, p.material_number, p.name, p.category,
               COALESCE(SUM(ii.net_amount), 0) AS revenue,
               COUNT(DISTINCT CASE WHEN i.billing_type = 'F2' THEN i.order_id END) AS order_count,
               COALESCE(SUM(ii.quantity), 0) AS quantity_sold,
               COALESCE(SUM(ii.cost_amount), 0) AS cost
        FROM products p
        JOIN invoice_items ii ON ii.product_id = p.id
        JOIN invoices i ON ii.invoice_id = i.id
        GROUP BY p.id, p.material_number, p.name, p.category
        HAVING COALESCE(SUM(ii.net_amount), 0) > 0
        ORDER BY revenue DESC
        """, nativeQuery = true)
    List<Object[]> getProductRevenue();
}
