package com.thomas.order_management.service;

import com.thomas.order_management.dto.CustomerDTO;
import com.thomas.order_management.dto.CustomerRequestDTO;
import java.util.List;

public interface CustomerService {
    List<CustomerDTO> getAllCustomers();
    CustomerDTO getCustomerById(Long id);
    CustomerDTO createCustomer(CustomerRequestDTO customerDTO);
    CustomerDTO updateCustomer(Long id, CustomerRequestDTO customerDetails);
    void deleteCustomer(Long id);
    CustomerDTO getCustomerByEmail(String email);
    List<CustomerDTO> searchCustomers(String query);
    long getCustomerCount();
}