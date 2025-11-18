// order-management/src/main/java/com/thomas/order_management/service/CustomerServiceImpl.java
package com.thomas.order_management.service;

import com.thomas.order_management.dto.CustomerDTO;
import com.thomas.order_management.dto.CustomerRequestDTO;
import com.thomas.order_management.exception.ConflictException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.CustomerMapper;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional 
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    public CustomerServiceImpl(CustomerRepository customerRepository, CustomerMapper customerMapper) {
        this.customerRepository = customerRepository;
        this.customerMapper = customerMapper;
    }

    @Override
    @Transactional(readOnly = true) 
    public List<CustomerDTO> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        return customerMapper.toCustomerDTOs(customers);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDTO getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kunde mit ID " + id + " nicht gefunden"));
        return customerMapper.toCustomerDTO(customer);
    }

    @Override
    public CustomerDTO createCustomer(CustomerRequestDTO customerDTO) {

        customerRepository.findByEmail(customerDTO.getEmail()).ifPresent(c -> {
            throw new ConflictException("E-Mail " + customerDTO.getEmail() + " wird bereits verwendet");
        });
        
        Customer customer = customerMapper.toCustomer(customerDTO);
        Customer savedCustomer = customerRepository.save(customer);
        return customerMapper.toCustomerDTO(savedCustomer);
    }

    @Override
    public CustomerDTO updateCustomer(Long id, CustomerRequestDTO customerDetails) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kunde mit ID " + id + " nicht gefunden"));

        if (customerDetails.getEmail() != null && !customerDetails.getEmail().equals(customer.getEmail())) {
             customerRepository.findByEmail(customerDetails.getEmail()).ifPresent(c -> {
                throw new ConflictException("E-Mail " + customerDetails.getEmail() + " wird bereits verwendet");
            });
        }

        customerMapper.updateCustomerFromDto(customerDetails, customer);
        
        Customer updatedCustomer = customerRepository.save(customer);
        return customerMapper.toCustomerDTO(updatedCustomer);
    }

    @Override
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Kunde mit ID " + id + " nicht gefunden");
        }
        customerRepository.deleteById(id);
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
        List<Customer> customers = customerRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(query, query);
        return customerMapper.toCustomerDTOs(customers);
    }

    @Override
    @Transactional(readOnly = true)
    public long getCustomerCount() {
        return customerRepository.countCustomers();
    }
}