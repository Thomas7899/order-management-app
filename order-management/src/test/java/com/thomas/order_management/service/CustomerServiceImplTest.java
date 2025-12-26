package com.thomas.order_management.service;

import com.thomas.order_management.dto.CustomerDTO;
import com.thomas.order_management.dto.CustomerRequestDTO;
import com.thomas.order_management.exception.ConflictException;
import com.thomas.order_management.exception.ResourceNotFoundException;
import com.thomas.order_management.mapper.CustomerMapper;
import com.thomas.order_management.model.Customer;
import com.thomas.order_management.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests für CustomerServiceImpl.
 * Testet CRUD-Operationen und Exception-Handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService Unit Tests")
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer testCustomer;
    private CustomerDTO testCustomerDTO;
    private CustomerRequestDTO testRequestDTO;

    @BeforeEach
    void setUp() {
        // Test-Customer Entity
        testCustomer = new Customer("Max", "Mustermann", "max@example.com");
        testCustomer.setId(1L);
        testCustomer.setPhone("+49 123 456789");
        testCustomer.setCity("München");

        // Test-Customer DTO
        testCustomerDTO = new CustomerDTO();
        testCustomerDTO.setId(1L);
        testCustomerDTO.setFirstName("Max");
        testCustomerDTO.setLastName("Mustermann");
        testCustomerDTO.setEmail("max@example.com");

        // Test-Request DTO
        testRequestDTO = new CustomerRequestDTO();
        testRequestDTO.setFirstName("Max");
        testRequestDTO.setLastName("Mustermann");
        testRequestDTO.setEmail("max@example.com");
    }

    @Nested
    @DisplayName("getAllCustomers Tests")
    class GetAllCustomersTests {

        @Test
        @DisplayName("Sollte alle Kunden zurückgeben")
        void shouldReturnAllCustomers() {
            // Given
            Customer customer2 = new Customer("Anna", "Schmidt", "anna@example.com");
            customer2.setId(2L);
            List<Customer> customers = Arrays.asList(testCustomer, customer2);

            CustomerDTO dto2 = new CustomerDTO();
            dto2.setId(2L);
            dto2.setFirstName("Anna");
            List<CustomerDTO> dtos = Arrays.asList(testCustomerDTO, dto2);

            when(customerRepository.findAll()).thenReturn(customers);
            when(customerMapper.toCustomerDTOs(customers)).thenReturn(dtos);

            // When
            List<CustomerDTO> result = customerService.getAllCustomers();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getFirstName()).isEqualTo("Max");
            assertThat(result.get(1).getFirstName()).isEqualTo("Anna");
            verify(customerRepository).findAll();
        }
    }

    @Nested
    @DisplayName("getCustomerById Tests")
    class GetCustomerByIdTests {

        @Test
        @DisplayName("Sollte Kunde nach ID finden")
        void shouldFindCustomerById() {
            // Given
            when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
            when(customerMapper.toCustomerDTO(testCustomer)).thenReturn(testCustomerDTO);

            // When
            CustomerDTO result = customerService.getCustomerById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("max@example.com");
            verify(customerRepository).findById(1L);
        }

        @Test
        @DisplayName("Sollte ResourceNotFoundException werfen wenn Kunde nicht existiert")
        void shouldThrowExceptionWhenCustomerNotFound() {
            // Given
            when(customerRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> customerService.getCustomerById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Kunde mit ID 999 nicht gefunden");

            verify(customerRepository).findById(999L);
            verify(customerMapper, never()).toCustomerDTO(any());
        }
    }

    @Nested
    @DisplayName("createCustomer Tests")
    class CreateCustomerTests {

        @Test
        @DisplayName("Sollte neuen Kunden erstellen")
        void shouldCreateNewCustomer() {
            // Given
            when(customerRepository.findByEmail("max@example.com"))
                .thenReturn(Optional.empty());
            when(customerMapper.toCustomer(testRequestDTO)).thenReturn(testCustomer);
            when(customerRepository.save(testCustomer)).thenReturn(testCustomer);
            when(customerMapper.toCustomerDTO(testCustomer)).thenReturn(testCustomerDTO);

            // When
            CustomerDTO result = customerService.createCustomer(testRequestDTO);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("Max");
            assertThat(result.getEmail()).isEqualTo("max@example.com");
            verify(customerRepository).findByEmail("max@example.com");
            verify(customerRepository).save(testCustomer);
        }

        @Test
        @DisplayName("Sollte ConflictException werfen wenn E-Mail bereits existiert")
        void shouldThrowConflictExceptionWhenEmailExists() {
            // Given
            when(customerRepository.findByEmail("max@example.com"))
                .thenReturn(Optional.of(testCustomer));

            // When & Then
            assertThatThrownBy(() -> customerService.createCustomer(testRequestDTO))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("E-Mail max@example.com wird bereits verwendet");

            verify(customerRepository).findByEmail("max@example.com");
            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateCustomer Tests")
    class UpdateCustomerTests {

        @Test
        @DisplayName("Sollte Kunde aktualisieren")
        void shouldUpdateCustomer() {
            // Given
            CustomerRequestDTO updateRequest = new CustomerRequestDTO();
            updateRequest.setFirstName("Maximilian");
            updateRequest.setLastName("Mustermann");
            updateRequest.setEmail("max@example.com"); // Gleiche E-Mail

            CustomerDTO updatedDTO = new CustomerDTO();
            updatedDTO.setId(1L);
            updatedDTO.setFirstName("Maximilian");

            when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
            when(customerRepository.save(any(Customer.class))).thenReturn(testCustomer);
            when(customerMapper.toCustomerDTO(any(Customer.class))).thenReturn(updatedDTO);

            // When
            CustomerDTO result = customerService.updateCustomer(1L, updateRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("Maximilian");
            verify(customerRepository).findById(1L);
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("Sollte ConflictException werfen bei E-Mail-Änderung zu existierender E-Mail")
        void shouldThrowConflictWhenChangingToExistingEmail() {
            // Given
            Customer existingOtherCustomer = new Customer("Anna", "Schmidt", "anna@example.com");
            existingOtherCustomer.setId(2L);

            CustomerRequestDTO updateRequest = new CustomerRequestDTO();
            updateRequest.setEmail("anna@example.com"); // Versucht E-Mail zu ändern

            when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
            when(customerRepository.findByEmail("anna@example.com"))
                .thenReturn(Optional.of(existingOtherCustomer));

            // When & Then
            assertThatThrownBy(() -> customerService.updateCustomer(1L, updateRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("E-Mail anna@example.com wird bereits verwendet");

            verify(customerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteCustomer Tests")
    class DeleteCustomerTests {

        @Test
        @DisplayName("Sollte Kunde löschen wenn er existiert")
        void shouldDeleteCustomerWhenExists() {
            // Given
            when(customerRepository.existsById(1L)).thenReturn(true);
            doNothing().when(customerRepository).deleteById(1L);

            // When
            customerService.deleteCustomer(1L);

            // Then
            verify(customerRepository).existsById(1L);
            verify(customerRepository).deleteById(1L);
        }

        @Test
        @DisplayName("Sollte ResourceNotFoundException werfen wenn Kunde nicht existiert")
        void shouldThrowExceptionWhenDeletingNonExistentCustomer() {
            // Given
            when(customerRepository.existsById(999L)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> customerService.deleteCustomer(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Kunde mit ID 999 nicht gefunden");

            verify(customerRepository, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("searchCustomers Tests")
    class SearchCustomersTests {

        @Test
        @DisplayName("Sollte Kunden nach Namen suchen")
        void shouldSearchCustomersByName() {
            // Given
            List<Customer> foundCustomers = Arrays.asList(testCustomer);
            List<CustomerDTO> foundDTOs = Arrays.asList(testCustomerDTO);

            when(customerRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("Max", "Max"))
                .thenReturn(foundCustomers);
            when(customerMapper.toCustomerDTOs(foundCustomers)).thenReturn(foundDTOs);

            // When
            List<CustomerDTO> result = customerService.searchCustomers("Max");

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFirstName()).isEqualTo("Max");
        }
    }

    @Nested
    @DisplayName("getCustomerCount Tests")
    class GetCustomerCountTests {

        @Test
        @DisplayName("Sollte Anzahl der Kunden zurückgeben")
        void shouldReturnCustomerCount() {
            // Given
            when(customerRepository.countCustomers()).thenReturn(42L);

            // When
            long result = customerService.getCustomerCount();

            // Then
            assertThat(result).isEqualTo(42L);
            verify(customerRepository).countCustomers();
        }
    }
}
