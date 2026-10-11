package com.thomas.order_management.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Auslieferung (SAP: Lieferart LF). Der Warenausgang bucht den Bestand aus dem Lagerort aus.
 */
@Entity
@Table(name = "deliveries", indexes = {
        @Index(name = "idx_deliveries_order", columnList = "order_id"),
        @Index(name = "idx_deliveries_status", columnList = "status")
})
@Getter
@Setter
public class Delivery {

    public enum Status {
        CREATED("Angelegt"),
        GOODS_ISSUED("Warenausgang gebucht"),
        CANCELLED("Storniert");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "delivery_number", nullable = false, unique = true, length = 10)
    private String deliveryNumber;

    @Column(name = "delivery_type", nullable = false, length = 4)
    private String deliveryType = "LF";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Lagerort, aus dem ausgeliefert wird. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false, length = 20)
    private ProcessingStatus billingStatus = ProcessingStatus.NOT_PROCESSED;

    @Column(name = "planned_goods_issue_date", nullable = false)
    private LocalDate plannedGoodsIssueDate;

    @Column(name = "goods_issue_date")
    private LocalDateTime goodsIssueDate;

    /** Materialbelegnummer des Warenausgangs. */
    @Column(name = "goods_issue_document", length = 10)
    private String goodsIssueDocument;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("itemNumber ASC")
    private List<DeliveryItem> items = new ArrayList<>();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public void addItem(DeliveryItem item) {
        item.setDelivery(this);
        items.add(item);
    }

    public boolean isGoodsIssued() {
        return status == Status.GOODS_ISSUED;
    }
}
