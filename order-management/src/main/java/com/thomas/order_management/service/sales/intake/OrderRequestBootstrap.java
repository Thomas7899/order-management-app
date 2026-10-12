package com.thomas.order_management.service.sales.intake;

import com.thomas.order_management.dto.sales.OrderRequestDtos.SampleText;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.SalesOrderRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Nach dem Start: einmalig drei Beispielbestellungen in den Auftragseingang legen (Demo) und
 * noch nicht ausgelesene Eingänge im Hintergrund auslesen.
 */
@Slf4j
@Component
public class OrderRequestBootstrap {

    private final OrderRequestService service;
    private final OrderRequestSamples samples;
    private final SalesOrderRequestRepository repository;
    private final CustomerRepository customerRepository;

    @Value("${app.demo.seed-data:true}")
    private boolean seedDemoData;

    public OrderRequestBootstrap(OrderRequestService service, OrderRequestSamples samples,
                                 SalesOrderRequestRepository repository, CustomerRepository customerRepository) {
        this.service = service;
        this.samples = samples;
        this.repository = repository;
        this.customerRepository = customerRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            if (seedDemoData && repository.count() == 0 && customerRepository.count() > 0) {
                seedDemoRequests();
            }
        } catch (RuntimeException ex) {
            log.warn("Beispielbestellungen konnten nicht angelegt werden", ex);
        }
        Thread.ofVirtual().name("order-request-extraction").start(() -> {
            try {
                int count = service.extractMissing();
                if (count > 0) log.info("{} Auftragseingänge ausgelesen", count);
            } catch (RuntimeException ex) {
                log.warn("Auslesen der Auftragseingänge fehlgeschlagen", ex);
            }
        });
    }

    private void seedDemoRequests() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        service.receive(new OrderRequestService.Incoming(null, null, "Bestellung PO-" + (45000 + today.getDayOfYear()),
                "bestellung.pdf", "application/pdf", samples.pdf(today)), now.minusHours(26));
        SampleText structured = samples.text(OrderRequestSamples.STRUCTURED, today);
        service.receive(new OrderRequestService.Incoming(structured.text(), structured.sender(), structured.subject(),
                null, null, null), now.minusHours(5));
        SampleText informal = samples.text(OrderRequestSamples.INFORMAL, today);
        service.receive(new OrderRequestService.Incoming(informal.text(), informal.sender(), informal.subject(),
                null, null, null), now.minusMinutes(40));
        log.info("Drei Beispielbestellungen im Auftragseingang angelegt");
    }
}
