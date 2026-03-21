package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private InvoiceBillRepository invoiceBillRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RolesRepository rolesRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer testCustomer;
    private Roles customerRole;

    @BeforeEach
    void setUp() {
        customerRole = new Roles();
        customerRole.setId(1L);
        customerRole.setRoleType("CUSTOMER");

        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");
        testCustomer.setUsername("testcustomer");
        testCustomer.setStatus("ACTIVE");
        testCustomer.setRole(customerRole);
        testCustomer.setConsumedLiters(BigDecimal.ZERO);
        testCustomer.setCreditLimitAmount(new BigDecimal("50000"));
        testCustomer.setCreditLimitLiters(new BigDecimal("1000"));
    }

    // --- getCustomers ---

    @Test
    void getCustomers_noFilters_returnsAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(testCustomer));
        when(customerRepository.findByGroupAndStatus(isNull(), isNull(), eq(pageable))).thenReturn(page);

        Page<Customer> result = customerService.getCustomers(null, null, null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getCustomers_withSearch_filtersBySearchTerm() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(testCustomer));
        when(customerRepository.findBySearchAndFilters(eq("test"), isNull(), isNull(), eq(pageable))).thenReturn(page);

        Page<Customer> result = customerService.getCustomers("test", null, null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getCustomers_withGroupId_filtersByGroup() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(testCustomer));
        when(customerRepository.findByGroupAndStatus(eq(1L), isNull(), eq(pageable))).thenReturn(page);

        Page<Customer> result = customerService.getCustomers(null, 1L, null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getCustomers_withSearchAndGroupId_filtersByBoth() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(testCustomer));
        when(customerRepository.findBySearchAndFilters(eq("test"), eq(1L), isNull(), eq(pageable))).thenReturn(page);

        Page<Customer> result = customerService.getCustomers("test", 1L, null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getCustomers_withStatus_filtersByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Customer> page = new PageImpl<>(List.of(testCustomer));
        when(customerRepository.findByGroupAndStatus(isNull(), eq("ACTIVE"), eq(pageable))).thenReturn(page);

        Page<Customer> result = customerService.getCustomers(null, null, "ACTIVE", pageable);

        assertEquals(1, result.getTotalElements());
    }

    // --- getCustomerById ---

    @Test
    void getCustomerById_exists_returnsCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        Customer result = customerService.getCustomerById(1L);

        assertEquals("Test Customer", result.getName());
    }

    @Test
    void getCustomerById_notExists_throwsException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> customerService.getCustomerById(99L));
        assertTrue(ex.getMessage().contains("Customer not found"));
    }

    // --- createCustomer ---

    @Test
    void createCustomer_setsDefaultsWhenNull() {
        Customer newCustomer = new Customer();
        newCustomer.setName("New Customer");
        newCustomer.setUsername("newcustomer");

        when(rolesRepository.findByRoleType("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.createCustomer(newCustomer);

        assertEquals("ACTIVE", result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getConsumedLiters());
        assertEquals(customerRole, result.getRole());
    }

    @Test
    void createCustomer_preservesExistingValues() {
        testCustomer.setStatus("INACTIVE");
        testCustomer.setConsumedLiters(new BigDecimal("100"));

        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.createCustomer(testCustomer);

        assertEquals("INACTIVE", result.getStatus());
        assertEquals(new BigDecimal("100"), result.getConsumedLiters());
    }

    // --- updateCustomer ---

    @Test
    void updateCustomer_updatesFields() {
        Customer updated = new Customer();
        updated.setName("Updated Name");
        updated.setUsername("updateduser");
        updated.setAddress("New Address");
        updated.setCreditLimitAmount(new BigDecimal("100000"));
        updated.setCreditLimitLiters(new BigDecimal("2000"));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.updateCustomer(1L, updated);

        assertEquals("Updated Name", result.getName());
        assertEquals("updateduser", result.getUsername());
        assertEquals("New Address", result.getAddress());
        assertEquals(new BigDecimal("100000"), result.getCreditLimitAmount());
    }

    // --- deleteCustomer ---

    @Test
    void deleteCustomer_unlinksVehiclesAndDeletes() {
        Vehicle v1 = new Vehicle();
        v1.setId(1L);
        v1.setCustomer(testCustomer);
        Vehicle v2 = new Vehicle();
        v2.setId(2L);
        v2.setCustomer(testCustomer);
        testCustomer.setVehicles(List.of(v1, v2));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        customerService.deleteCustomer(1L);

        assertNull(v1.getCustomer());
        assertNull(v2.getCustomer());
        verify(vehicleRepository).saveAll(anyList());
        verify(customerRepository).delete(testCustomer);
    }

    // --- toggleStatus ---

    @Test
    void toggleStatus_activeToInactive() {
        testCustomer.setStatus("ACTIVE");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.toggleStatus(1L);

        assertEquals("INACTIVE", result.getStatus());
    }

    @Test
    void toggleStatus_inactiveToActive() {
        testCustomer.setStatus("INACTIVE");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.toggleStatus(1L);

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void toggleStatus_blockedToActive() {
        testCustomer.setStatus("BLOCKED");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.toggleStatus(1L);

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void toggleStatus_nullToInactive() {
        testCustomer.setStatus(null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.toggleStatus(1L);

        assertEquals("INACTIVE", result.getStatus());
    }

    // --- blockCustomer ---

    @Test
    void blockCustomer_activeCustomer_blocks() {
        testCustomer.setStatus("ACTIVE");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.blockCustomer(1L);

        assertEquals("BLOCKED", result.getStatus());
    }

    @Test
    void blockCustomer_inactiveCustomer_throwsException() {
        testCustomer.setStatus("INACTIVE");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> customerService.blockCustomer(1L));
        assertTrue(ex.getMessage().contains("can only be blocked when ACTIVE"));
    }

    // --- unblockCustomer ---

    @Test
    void unblockCustomer_blockedCustomer_unblocks() {
        testCustomer.setStatus("BLOCKED");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.unblockCustomer(1L);

        assertEquals("ACTIVE", result.getStatus());
    }

    @Test
    void unblockCustomer_activeCustomer_throwsException() {
        testCustomer.setStatus("ACTIVE");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> customerService.unblockCustomer(1L));
        assertTrue(ex.getMessage().contains("can only be unblocked when BLOCKED"));
    }

    // --- checkAndAutoBlock ---

    @Test
    void checkAndAutoBlock_amountExceeded_blocks() {
        testCustomer.setCreditLimitAmount(new BigDecimal("10000"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(invoiceBillRepository.sumAllCreditBillsByCustomer(1L)).thenReturn(new BigDecimal("15000"));
        when(paymentRepository.sumAllPaymentsByCustomer(1L)).thenReturn(new BigDecimal("2000"));

        customerService.checkAndAutoBlock(1L);

        assertEquals("BLOCKED", testCustomer.getStatus());
        verify(customerRepository).save(testCustomer);
    }

    @Test
    void checkAndAutoBlock_litersExceeded_blocks() {
        testCustomer.setCreditLimitAmount(null); // skip amount check
        testCustomer.setCreditLimitLiters(new BigDecimal("1000"));
        testCustomer.setConsumedLiters(new BigDecimal("1000"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        customerService.checkAndAutoBlock(1L);

        assertEquals("BLOCKED", testCustomer.getStatus());
    }

    @Test
    void checkAndAutoBlock_aging90Days_blocks() {
        testCustomer.setCreditLimitAmount(null);
        testCustomer.setCreditLimitLiters(null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(invoiceBillRepository.existsUnpaidCreditBillBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(true);

        customerService.checkAndAutoBlock(1L);

        assertEquals("BLOCKED", testCustomer.getStatus());
    }

    @Test
    void checkAndAutoBlock_noTriggers_remainsActive() {
        testCustomer.setCreditLimitAmount(new BigDecimal("50000"));
        testCustomer.setCreditLimitLiters(new BigDecimal("5000"));
        testCustomer.setConsumedLiters(new BigDecimal("100"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(invoiceBillRepository.sumAllCreditBillsByCustomer(1L)).thenReturn(new BigDecimal("5000"));
        when(paymentRepository.sumAllPaymentsByCustomer(1L)).thenReturn(new BigDecimal("3000"));
        when(invoiceBillRepository.existsUnpaidCreditBillBefore(eq(1L), any(LocalDateTime.class)))
                .thenReturn(false);

        customerService.checkAndAutoBlock(1L);

        assertEquals("ACTIVE", testCustomer.getStatus());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void checkAndAutoBlock_alreadyBlocked_doesNothing() {
        testCustomer.setStatus("BLOCKED");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        customerService.checkAndAutoBlock(1L);

        verify(invoiceBillRepository, never()).sumAllCreditBillsByCustomer(any());
    }

    @Test
    void checkAndAutoBlock_customerNotFound_doesNothing() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        customerService.checkAndAutoBlock(99L);

        verify(customerRepository, never()).save(any());
    }

    // --- resetConsumedLiters ---

    @Test
    void resetConsumedLiters_resetsAllCustomersAndVehicles() {
        Customer c1 = new Customer();
        c1.setConsumedLiters(new BigDecimal("500"));
        Customer c2 = new Customer();
        c2.setConsumedLiters(new BigDecimal("300"));

        Vehicle v1 = new Vehicle();
        v1.setConsumedLiters(new BigDecimal("200"));

        when(customerRepository.findAll()).thenReturn(List.of(c1, c2));
        when(vehicleRepository.findAll()).thenReturn(List.of(v1));

        customerService.resetConsumedLiters();

        assertEquals(BigDecimal.ZERO, c1.getConsumedLiters());
        assertEquals(BigDecimal.ZERO, c2.getConsumedLiters());
        assertEquals(BigDecimal.ZERO, v1.getConsumedLiters());
        verify(customerRepository).saveAll(anyList());
        verify(vehicleRepository).saveAll(anyList());
    }

    // --- getStats ---

    @Test
    void getStats_calculatesCorrectly() {
        Customer active = new Customer();
        active.setStatus("ACTIVE");
        active.setCreditLimitLiters(new BigDecimal("1000"));
        active.setConsumedLiters(new BigDecimal("500"));

        Customer blocked = new Customer();
        blocked.setStatus("BLOCKED");
        blocked.setCreditLimitLiters(new BigDecimal("2000"));
        blocked.setConsumedLiters(new BigDecimal("1500"));

        when(customerRepository.findAll()).thenReturn(List.of(active, blocked));

        Map<String, Object> stats = customerService.getStats();

        assertEquals(2L, stats.get("totalCustomers"));
        assertEquals(1L, stats.get("activeFleets"));
        assertEquals(1L, stats.get("blockedCustomers"));
        assertEquals(new BigDecimal("3000"), stats.get("totalCreditGiven"));
        assertEquals(new BigDecimal("2000"), stats.get("totalConsumed"));
    }
}
