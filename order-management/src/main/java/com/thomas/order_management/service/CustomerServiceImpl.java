// order-management/src/main/java/com/thomas/order_management/service/CustomerServiceImpl.java
package com.thomas.order_management.service;

import com.thomas.order_management.dto.CustomerDTO;
import com.thomas.order_management.dto.CustomerRequestDTO;
import com.thomas.order_management.exception.BusinessException;
import com.thomas.order_management.exception.ConflictException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.CustomerMapper;
import com.thomas.order_management.model.ChangeDocument;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.model.NumberRangeObject;
import com.thomas.order_management.model.PaymentTerms;
import com.thomas.order_management.repository.CustomerRepository;
import com.thomas.order_management.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final CustomerMapper customerMapper;
    private final NumberRangeService numberRangeService;
    private final ChangeDocumentService changeDocuments;

    public CustomerServiceImpl(CustomerRepository customerRepository,
                               OrderRepository orderRepository,
                               CustomerMapper customerMapper,
                               NumberRangeService numberRangeService,
                               ChangeDocumentService changeDocuments) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.customerMapper = customerMapper;
        this.numberRangeService = numberRangeService;
        this.changeDocuments = changeDocuments;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDTO> getAllCustomers() {
        return customerMapper.toCustomerDTOs(customerRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDTO getCustomerById(Long id) {
        return customerMapper.toCustomerDTO(load(id));
    }

    @Override
    public CustomerDTO createCustomer(CustomerRequestDTO request) {
        customerRepository.findByEmail(request.getEmail()).ifPresent(c -> {
            throw new ConflictException("E-Mail " + request.getEmail() + " wird bereits verwendet");
        });

        Customer customer = customerMapper.toCustomer(request);
        customer.setCustomerNumber(numberRangeService.next(NumberRangeObject.CUSTOMER));
        if (customer.getPaymentTerms() == null) customer.setPaymentTerms(PaymentTerms.ZB30);
        if (customer.getDiscountPercent() == null) customer.setDiscountPercent(BigDecimal.ZERO);
        if (customer.getActive() == null) customer.setActive(true);

        Customer saved = customerRepository.save(customer);
        changeDocuments.logEvent(ChangeDocument.ObjectClass.CUSTOMER, saved.getId(), saved.getCustomerNumber(),
                ChangeDocument.ChangeType.INSERT, "Debitor angelegt");
        return customerMapper.toCustomerDTO(saved);
    }

    @Override
    public CustomerDTO updateCustomer(Long id, CustomerRequestDTO request) {
        Customer customer = load(id);
        if (request.getEmail() != null && !request.getEmail().equals(customer.getEmail())) {
            customerRepository.findByEmail(request.getEmail()).ifPresent(c -> {
                throw new ConflictException("E-Mail " + request.getEmail() + " wird bereits verwendet");
            });
        }

        String number = customer.getCustomerNumber();
        ChangeDocument.ObjectClass oc = ChangeDocument.ObjectClass.CUSTOMER;
        changeDocuments.logFieldChange(oc, id, number, "E-Mail", customer.getEmail(), request.getEmail());
        changeDocuments.logFieldChange(oc, id, number, "Adresse", customer.getFormattedAddress(),
                preview(request));
        if (request.getPaymentTerms() != null) {
            changeDocuments.logFieldChange(oc, id, number, "Zahlungsbedingung", customer.getPaymentTerms(), request.getPaymentTerms());
        }
        if (request.getCreditLimit() != null) {
            changeDocuments.logFieldChange(oc, id, number, "Kreditlimit", customer.getCreditLimit(), request.getCreditLimit());
        }
        if (request.getDiscountPercent() != null) {
            changeDocuments.logFieldChange(oc, id, number, "Kundenrabatt %", customer.getDiscountPercent(), request.getDiscountPercent());
        }
        if (request.getActive() != null) {
            changeDocuments.logFieldChange(oc, id, number, "Gesperrt", !Boolean.TRUE.equals(customer.getActive()), !request.getActive());
        }

        customerMapper.updateCustomerFromDto(request, customer);
        return customerMapper.toCustomerDTO(customerRepository.save(customer));
    }

    @Override
    public void deleteCustomer(Long id) {
        Customer customer = load(id);
        if (!orderRepository.findByCustomerIdOrderByOrderDateDesc(id).isEmpty()) {
            throw new BusinessException("F2", "105", "Debitor " + customer.getCustomerNumber()
                    + " hat Belege und kann nicht gelöscht werden; bitte stattdessen sperren", HttpStatus.CONFLICT);
        }
        customerRepository.delete(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDTO getCustomerByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Kunde mit E-Mail " + email + " nicht gefunden"));
        return customerMapper.toCustomerDTO(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDTO> searchCustomers(String query) {
        return customerMapper.toCustomerDTOs(customerRepository.search(query.trim().toLowerCase()));
    }

    @Override
    @Transactional(readOnly = true)
    public long getCustomerCount() {
        return customerRepository.count();
    }

    private Customer load(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kunde mit ID " + id + " nicht gefunden"));
    }

    private static String preview(CustomerRequestDTO r) {
        Customer tmp = new Customer(r.getFirstName(), r.getLastName(), r.getEmail());
        tmp.setAddress(r.getAddress());
        tmp.setZipCode(r.getZipCode());
        tmp.setCity(r.getCity());
        tmp.setCountry(r.getCountry());
        return tmp.getFormattedAddress();
    }
}
