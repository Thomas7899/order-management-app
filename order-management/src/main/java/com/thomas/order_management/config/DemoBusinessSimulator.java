package com.thomas.order_management.config;

import com.thomas.order_management.dto.sales.OrderRequests;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.model.*;
import com.thomas.order_management.model.StockMovement.MovementType;
import com.thomas.order_management.model.StockMovement.ReferenceType;
import com.thomas.order_management.repository.*;
import com.thomas.order_management.service.StockPostingService;
import com.thomas.order_management.service.sales.BillingService;
import com.thomas.order_management.service.sales.DeliveryService;
import com.thomas.order_management.service.sales.SalesOrderService;
import com.thomas.order_management.dto.sales.LogisticsRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;
import java.util.function.Supplier;

/**
 * Erzeugt ein Geschäftsjahr an Demo-Daten, indem der Order-to-Cash-Prozess Tag für Tag über die
 * echten Services durchlaufen wird. Dadurch stimmen Belegfluss, Bestände, Umsätze und offene Posten
 * miteinander überein.
 */
@Component
public class DemoBusinessSimulator {

    private static final Logger log = LoggerFactory.getLogger(DemoBusinessSimulator.class);

    static final String USER_SALES = "MREUTER";
    static final String USER_WAREHOUSE = "JMEIER";
    static final String USER_BILLING = "PRICHTER";
    static final String USER_CREDIT = "AKOCH";

    private final SalesOrderService orderService;
    private final DeliveryService deliveryService;
    private final BillingService billingService;
    private final StockPostingService stockPostingService;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final InvoiceRepository invoiceRepository;

    public DemoBusinessSimulator(SalesOrderService orderService,
                                 DeliveryService deliveryService,
                                 BillingService billingService,
                                 StockPostingService stockPostingService,
                                 WarehouseRepository warehouseRepository,
                                 WarehouseStockRepository stockRepository,
                                 OrderRepository orderRepository,
                                 DeliveryRepository deliveryRepository,
                                 InvoiceRepository invoiceRepository) {
        this.orderService = orderService;
        this.deliveryService = deliveryService;
        this.billingService = billingService;
        this.stockPostingService = stockPostingService;
        this.warehouseRepository = warehouseRepository;
        this.stockRepository = stockRepository;
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /** Zahlungsverhalten eines Debitors. */
    private enum PaymentBehaviour { DISCOUNT, ON_TIME, LATE, NON_PAYER }

    private record PlannedDelivery(Long orderId, LocalDate date) {
    }

    private record PlannedPayment(Long invoiceId, LocalDate date) {
    }

    public void simulate(List<Customer> customers, List<Product> products, Set<Long> trendingUp,
                         Set<Long> trendingDown, Random random) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(365);

        List<Warehouse> warehouses = asUser(USER_WAREHOUSE, () -> seedWarehouses(products, start.minusDays(1), random));
        Map<Long, PaymentBehaviour> behaviour = assignPaymentBehaviour(customers, random);
        List<Customer> weightedCustomers = weightCustomers(customers, random);
        Set<Long> supplierProblems = pickSupplierProblems(products, trendingUp, random);

        List<PlannedDelivery> plannedDeliveries = new ArrayList<>();
        List<PlannedPayment> plannedPayments = new ArrayList<>();
        Deque<Long> creditBlocked = new ArrayDeque<>();
        int orders = 0;

        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            final LocalDate d = day;
            boolean lastDays = !d.isBefore(today.minusDays(3));

            // 1. Wareneingänge (montags) und Rückstandsbearbeitung
            if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
                asUser(USER_WAREHOUSE, () -> replenish(warehouses, supplierProblems, d, today, random));
                asUser(USER_SALES, orderService::processBacklog);
            }

            // 2. Kreditfreigaben (nach 1–3 Tagen, außer für die letzten Tage)
            if (!lastDays) {
                asUser(USER_CREDIT, () -> releaseCreditBlocks(creditBlocked, random));
            }

            // 3. Auslieferungen inkl. Warenausgang
            Iterator<PlannedDelivery> it = plannedDeliveries.iterator();
            while (it.hasNext()) {
                PlannedDelivery planned = it.next();
                if (planned.date().isAfter(d)) continue;
                it.remove();
                asUser(USER_WAREHOUSE, () -> deliver(planned.orderId(), d, today, random));
            }

            // 4. Fakturalauf (VF04) für gestern ausgelieferte Lieferungen
            if (d.isBefore(today)) {
                asUser(USER_BILLING, () -> billingRun(d, behaviour, plannedPayments, today, random));
            }

            // 5. Zahlungseingänge
            Iterator<PlannedPayment> pit = plannedPayments.iterator();
            while (pit.hasNext()) {
                PlannedPayment payment = pit.next();
                if (payment.date().isAfter(d)) continue;
                pit.remove();
                asUser(USER_BILLING, () -> pay(payment, d));
            }

            // 6. Neue Kundenaufträge
            int count = ordersForDay(d, random);
            for (int i = 0; i < count; i++) {
                Order order = asUser(USER_SALES, () -> createOrder(d, weightedCustomers, products, trendingUp, trendingDown, today, random));
                if (order == null) continue;
                orders++;
                if (order.getCreditStatus() == CreditStatus.BLOCKED) {
                    creditBlocked.add(order.getId());
                }
                int leadDays = random.nextDouble() < 0.12 ? 3 + random.nextInt(5) : 1 + random.nextInt(2);
                plannedDeliveries.add(new PlannedDelivery(order.getId(), d.plusDays(leadDays)));

                if (random.nextDouble() < 0.035) {
                    asUser(USER_SALES, () -> rejectOrder(order.getId(), random));
                }
            }

            // 7. Sonstige Lagerbuchungen
            asUser(USER_WAREHOUSE, () -> miscellaneousPostings(warehouses, products, d, random));
        }

        asUser(USER_BILLING, () -> cancelSomeInvoices(random));
        log.info("Simulated {} sales orders from {} to {}", orders, start, today);
    }

    // ------------------------------------------------------------------
    // Lager
    // ------------------------------------------------------------------

    private List<Warehouse> seedWarehouses(List<Product> products, LocalDate date, Random random) {
        Warehouse main = warehouse("0001", "Hauptlager München", "Zentrales Lager, Werk 1000", "Industriestraße 15", "80339", "München", true);
        Warehouse north = warehouse("0002", "Außenlager Hamburg", "Regionallager Nord", "Hafenweg 42", "20457", "Hamburg", false);
        Warehouse east = warehouse("0003", "Lager Berlin", "Regionallager Ost", "Spreestraße 88", "10179", "Berlin", false);
        Warehouse south = warehouse("0004", "Lager Stuttgart", "Regionallager Süd", "Automobilstraße 12", "70173", "Stuttgart", false);
        List<Warehouse> warehouses = warehouseRepository.saveAll(List.of(main, north, east, south));

        LocalDateTime postingDate = date.atTime(7, 0);
        for (Product product : products) {
            boolean slowMover = random.nextDouble() < 0.08;
            int min = 8 + random.nextInt(8);
            int max = slowMover ? 40 + random.nextInt(20) : 50 + random.nextInt(40);
            String bin = String.format("%c-%02d-%d", (char) ('A' + random.nextInt(8)), 1 + random.nextInt(20), 1 + random.nextInt(5));

            stockRepository.save(stockSettings(warehouses.get(0), product, min, max, bin));
            int initial = slowMover ? max + 80 + random.nextInt(120) : max;
            post(MovementType.INITIAL_ENTRY, product, null, warehouses.get(0), initial, postingDate,
                    "Datenübernahme Anfangsbestand", ReferenceType.MANUAL, "MIGRATION");

            for (int w = 1; w < warehouses.size(); w++) {
                if (random.nextDouble() < (w == 1 ? 0.55 : 0.3)) {
                    String binW = String.format("%c-%02d-%d", (char) ('A' + random.nextInt(8)), 1 + random.nextInt(20), 1 + random.nextInt(5));
                    stockRepository.save(stockSettings(warehouses.get(w), product, 3, 25, binW));
                    post(MovementType.INITIAL_ENTRY, product, null, warehouses.get(w), 8 + random.nextInt(15),
                            postingDate, "Datenübernahme Anfangsbestand", ReferenceType.MANUAL, "MIGRATION");
                }
            }
        }
        log.info("Seeded {} storage locations with initial stock (561)", warehouses.size());
        return warehouses;
    }

    private Warehouse warehouse(String code, String name, String description, String street, String zip, String city, boolean isDefault) {
        Warehouse w = new Warehouse(code, name);
        w.setDescription(description);
        w.setAddress(street);
        w.setZipCode(zip);
        w.setCity(city);
        w.setCountry("Deutschland");
        w.setActive(true);
        w.setIsDefault(isDefault);
        return w;
    }

    private WarehouseStock stockSettings(Warehouse warehouse, Product product, int min, int max, String bin) {
        WarehouseStock stock = new WarehouseStock(warehouse, product, 0);
        stock.setMinStock(min);
        stock.setMaxStock(max);
        stock.setBinLocation(bin);
        return stock;
    }

    /** Wöchentlicher Nachschub: Lagerorte unter Mindestbestand werden auf Höchstbestand aufgefüllt. */
    private void replenish(List<Warehouse> warehouses, Set<Long> supplierProblems, LocalDate day, LocalDate today, Random random) {
        boolean supplierProblemPhase = !day.isBefore(today.minusDays(35));
        LocalDateTime postingDate = day.atTime(9, 30);
        int purchaseOrder = 4500000 + day.getDayOfYear() * 10;
        for (WarehouseStock stock : stockRepository.findLowStockItems()) {
            if (supplierProblemPhase && supplierProblems.contains(stock.getProduct().getId())) continue;
            int quantity = stock.getMaxStock() - stock.getQuantity();
            if (quantity <= 0) continue;
            post(MovementType.GOODS_RECEIPT, stock.getProduct(), null, stock.getWarehouse(), quantity, postingDate,
                    "Wareneingang zur Bestellung", ReferenceType.PURCHASE_ORDER, "45" + (purchaseOrder + random.nextInt(10)));
        }
    }

    private void miscellaneousPostings(List<Warehouse> warehouses, List<Product> products, LocalDate day, Random random) {
        LocalDateTime postingDate = day.atTime(14, 0);
        Product product = products.get(random.nextInt(products.size()));

        // Umlagerung 311 vom Hauptlager in ein Regionallager (ca. einmal pro Woche)
        if (random.nextDouble() < 0.14) {
            Warehouse target = warehouses.get(1 + random.nextInt(warehouses.size() - 1));
            tryPost(MovementType.TRANSFER, product, warehouses.get(0), target, 3 + random.nextInt(6), postingDate,
                    "Bestandsausgleich " + warehouses.get(0).getCity() + " → " + target.getCity(), ReferenceType.MANUAL, null);
        }
        // Inventurdifferenz 701/702 (Stichprobeninventur)
        if (random.nextDouble() < 0.05) {
            stockRepository.findByWarehouseIdAndProductId(warehouses.get(0).getId(), product.getId()).ifPresent(stock -> {
                int counted = Math.max(0, stock.getQuantity() + (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(2)));
                tryPost(MovementType.INVENTORY_ADJUSTMENT, product, null, stock.getWarehouse(), counted, postingDate,
                        "Stichprobeninventur", ReferenceType.INVENTORY, "INV" + day.getYear() + String.format("%04d", day.getDayOfYear()));
            });
        }
        // Verschrottung 551
        if (random.nextDouble() < 0.03) {
            tryPost(MovementType.SCRAP, product, warehouses.get(0), null, 1, postingDate,
                    "Transportschaden – verschrottet", ReferenceType.MANUAL, null);
        }
        // Retoure 651
        if (random.nextDouble() < 0.04) {
            tryPost(MovementType.RETURN, product, null, warehouses.get(0), 1, postingDate,
                    "Kundenretoure, Ware einwandfrei", ReferenceType.RETURN_ORDER, "6" + (1000000 + random.nextInt(900000)));
        }
    }

    private void post(MovementType type, Product product, Warehouse source, Warehouse target, int qty,
                      LocalDateTime date, String reason, ReferenceType refType, String refNumber) {
        stockPostingService.post(new StockPostingService.Posting(type, product, source, target, qty, date, reason, refType, refNumber));
    }

    private void tryPost(MovementType type, Product product, Warehouse source, Warehouse target, int qty,
                         LocalDateTime date, String reason, ReferenceType refType, String refNumber) {
        try {
            post(type, product, source, target, qty, date, reason, refType, refNumber);
        } catch (BusinessException ignored) {
            // z. B. kein ausreichender Bestand oder keine Inventurdifferenz – in der Realität würde die Buchung abgelehnt
        }
    }

    // ------------------------------------------------------------------
    // Vertrieb
    // ------------------------------------------------------------------

    private Order createOrder(LocalDate day, List<Customer> customers, List<Product> products,
                              Set<Long> trendingUp, Set<Long> trendingDown, LocalDate today, Random random) {
        Customer customer = customers.get(random.nextInt(customers.size()));
        for (int attempt = 0; attempt < 5 && customer.getCreatedAt().toLocalDate().isAfter(day); attempt++) {
            customer = customers.get(random.nextInt(customers.size()));
        }
        if (customer.getCreatedAt().toLocalDate().isAfter(day)) {
            return null;
        }
        boolean business = customer.getDiscountPercent() != null && customer.getDiscountPercent().signum() > 0;
        int itemCount = 1 + random.nextInt(business ? 5 : 3);

        List<OrderRequests.Item> items = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (int i = 0; i < itemCount; i++) {
            Product product = selectProduct(products, day, today, trendingUp, trendingDown, random);
            if (!used.add(product.getId())) continue;
            int quantity = business && random.nextDouble() < 0.25 ? 10 + random.nextInt(20) : 1 + random.nextInt(3);
            BigDecimal discount = business && random.nextDouble() < 0.1 ? BigDecimal.valueOf(2 + random.nextInt(4)) : null;
            items.add(new OrderRequests.Item(product.getId(), quantity, discount));
        }

        LocalDateTime documentDate = day.atTime(8 + random.nextInt(10), random.nextInt(60));
        String reference = business && random.nextDouble() < 0.7 ? "PO-" + (20000 + random.nextInt(80000)) : null;
        OrderRequests.Create request = new OrderRequests.Create(customer.getId(), day.plusDays(2 + random.nextInt(4)),
                reference, null, null, null, items, null);
        try {
            return orderService.createOrder(request, documentDate);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private Product selectProduct(List<Product> products, LocalDate day, LocalDate today, Set<Long> trendingUp,
                                  Set<Long> trendingDown, Random random) {
        long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(day, today);
        if (daysAgo < 90 && random.nextDouble() < 0.3 && !trendingUp.isEmpty()) {
            List<Product> candidates = products.stream().filter(p -> trendingUp.contains(p.getId())).toList();
            return candidates.get(random.nextInt(candidates.size()));
        }
        if (daysAgo > 180 && random.nextDouble() < 0.25 && !trendingDown.isEmpty()) {
            List<Product> candidates = products.stream().filter(p -> trendingDown.contains(p.getId())).toList();
            return candidates.get(random.nextInt(candidates.size()));
        }
        // Pareto-ähnliche Verteilung: die ersten Materialien der (gemischten) Liste verkaufen sich häufiger
        int index = (int) Math.min(products.size() - 1, Math.floor(Math.pow(random.nextDouble(), 1.6) * products.size()));
        return products.get(index);
    }

    private void deliver(Long orderId, LocalDate day, LocalDate today, Random random) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() == OrderStatus.CANCELLED || !order.getCreditStatus().allowsDelivery()) {
            return;
        }
        try {
            Delivery delivery = deliveryService.createDelivery(
                    new LogisticsRequests.CreateDelivery(orderId, null, day, null), day.atTime(10, 0));
            // Am heutigen Tag bleiben einige Lieferungen ohne Warenausgang (Kommissionierung läuft noch)
            if (day.equals(today) && random.nextDouble() < 0.6) return;
            deliveryService.postGoodsIssueAt(delivery.getId(), day.atTime(15, random.nextInt(60)));
        } catch (BusinessException ignored) {
            // Lagerortbestand reicht nicht – Lieferung bleibt offen und erscheint im Arbeitsvorrat
        }
    }

    private void billingRun(LocalDate day, Map<Long, PaymentBehaviour> behaviour, List<PlannedPayment> payments,
                            LocalDate today, Random random) {
        LocalDate billingDate = day;
        for (Delivery delivery : deliveryRepository.findBillingDueList(ProcessingStatus.NOT_PROCESSED)) {
            if (!delivery.getGoodsIssueDate().toLocalDate().isBefore(day)) continue;
            Invoice invoice = billingService.createInvoice(delivery.getId(), billingDate, billingDate.atTime(2, 0));
            LocalDate paymentDate = paymentDate(invoice, behaviour.get(invoice.getCustomer().getId()), random);
            if (paymentDate != null && !paymentDate.isAfter(today)) {
                payments.add(new PlannedPayment(invoice.getId(), paymentDate));
            }
        }
    }

    private LocalDate paymentDate(Invoice invoice, PaymentBehaviour behaviour, Random random) {
        return switch (behaviour) {
            case DISCOUNT -> invoice.getCashDiscountDate() != null
                    ? invoice.getBillingDate().plusDays(3 + random.nextInt(Math.max(1, invoice.getPaymentTerms().getCashDiscountDays() - 3)))
                    : invoice.getBillingDate().plusDays(5 + random.nextInt(10));
            case ON_TIME -> invoice.getDueDate().minusDays(random.nextInt(6));
            case LATE -> invoice.getDueDate().plusDays(5 + random.nextInt(30));
            case NON_PAYER -> random.nextDouble() < 0.5 ? invoice.getDueDate().plusDays(40 + random.nextInt(40)) : null;
        };
    }

    private void pay(PlannedPayment payment, LocalDate day) {
        try {
            billingService.postPayment(payment.invoiceId(), day, null);
        } catch (BusinessException ignored) {
            // Rechnung wurde zwischenzeitlich storniert
        }
    }

    private void releaseCreditBlocks(Deque<Long> blocked, Random random) {
        int releases = Math.min(blocked.size(), random.nextInt(3));
        for (int i = 0; i < releases; i++) {
            Long orderId = blocked.poll();
            try {
                orderService.releaseCreditBlock(orderId);
            } catch (BusinessException ignored) {
                // Auftrag wurde inzwischen abgesagt
            }
        }
    }

    private void rejectOrder(Long orderId, Random random) {
        RejectionReason reason = random.nextBoolean() ? RejectionReason.R10 : RejectionReason.R03;
        try {
            orderService.reject(orderId, new OrderRequests.Reject(reason, null));
        } catch (BusinessException ignored) {
            // bereits geliefert
        }
    }

    /** Einige Rechnungen werden storniert und neu fakturiert, damit der Belegfluss S1 zeigt. */
    private void cancelSomeInvoices(Random random) {
        List<Invoice> openInvoices = invoiceRepository.findOpenItems();
        Collections.shuffle(openInvoices, random);
        LocalDate today = LocalDate.now();
        openInvoices.stream().limit(3).forEach(invoice -> {
            Long deliveryId = invoice.getDelivery().getId();
            billingService.cancelInvoice(invoice.getId(), today);
            billingService.createInvoice(deliveryId, today, LocalDateTime.now());
        });
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    private int ordersForDay(LocalDate day, Random random) {
        double base = switch (day.getDayOfWeek()) {
            case SATURDAY -> 0.5;
            case SUNDAY -> 0.2;
            default -> 1.8;
        };
        Month month = day.getMonth();
        double season = switch (month) {
            case NOVEMBER, DECEMBER -> 1.5;
            case JANUARY, FEBRUARY -> 0.75;
            case JUNE, JULY, AUGUST -> 0.85;
            default -> 1.0;
        };
        double expected = base * season;
        int count = (int) expected;
        if (random.nextDouble() < expected - count) count++;
        return count;
    }

    private Map<Long, PaymentBehaviour> assignPaymentBehaviour(List<Customer> customers, Random random) {
        Map<Long, PaymentBehaviour> map = new HashMap<>();
        for (Customer customer : customers) {
            double r = random.nextDouble();
            PaymentBehaviour b = customer.getPaymentTerms() == PaymentTerms.ZB01 && r < 0.6 ? PaymentBehaviour.DISCOUNT
                    : r < 0.75 ? PaymentBehaviour.ON_TIME
                    : r < 0.95 ? PaymentBehaviour.LATE
                    : PaymentBehaviour.NON_PAYER;
            map.put(customer.getId(), b);
        }
        return map;
    }

    /** Geschäftskunden (mit Rabatt) bestellen deutlich häufiger als Privatkunden. */
    private List<Customer> weightCustomers(List<Customer> customers, Random random) {
        List<Customer> weighted = new ArrayList<>();
        for (Customer customer : customers) {
            boolean business = customer.getDiscountPercent() != null && customer.getDiscountPercent().signum() > 0;
            int weight = business ? 6 : 1;
            for (int i = 0; i < weight; i++) weighted.add(customer);
        }
        return weighted;
    }

    /** Einige Materialien haben in den letzten Wochen Lieferprobleme beim Lieferanten → Rückstand. */
    private Set<Long> pickSupplierProblems(List<Product> products, Set<Long> trendingUp, Random random) {
        List<Product> candidates = new ArrayList<>(products.subList(0, Math.min(25, products.size())));
        Collections.shuffle(candidates, random);
        Set<Long> result = new HashSet<>();
        candidates.stream().limit(4).forEach(p -> result.add(p.getId()));
        trendingUp.stream().findFirst().ifPresent(result::add);
        return result;
    }

    private <T> T asUser(String user, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_SYSTEM"))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private void asUser(String user, Runnable action) {
        asUser(user, () -> {
            action.run();
            return null;
        });
    }
}
