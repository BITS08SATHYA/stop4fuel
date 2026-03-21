package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Group;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.GroupRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MappingServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private InvoiceBillRepository invoiceBillRepository;

    @InjectMocks
    private MappingService mappingService;

    private Customer testCustomer;
    private Vehicle testVehicle;
    private Group testGroup;

    @BeforeEach
    void setUp() {
        testGroup = new Group();
        testGroup.setId(1L);
        testGroup.setGroupName("Fleet A");

        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");

        testVehicle = new Vehicle();
        testVehicle.setId(1L);
        testVehicle.setVehicleNumber("TN01AB1234");
    }

    @Test
    void getUnassignedCustomers_returnsCustomersWithNoGroup() {
        when(customerRepository.findByGroupIsNull()).thenReturn(List.of(testCustomer));
        assertEquals(1, mappingService.getUnassignedCustomers().size());
    }

    @Test
    void getUnassignedVehicles_returnsVehiclesWithNoCustomer() {
        when(vehicleRepository.findByCustomerIsNull()).thenReturn(List.of(testVehicle));
        assertEquals(1, mappingService.getUnassignedVehicles().size());
    }

    @Test
    void assignCustomersToGroup_setsGroupOnCustomers() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(customerRepository.findByIdIn(List.of(1L))).thenReturn(List.of(testCustomer));
        when(customerRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Customer> result = mappingService.assignCustomersToGroup(List.of(1L), 1L);
        assertEquals(testGroup, result.get(0).getGroup());
    }

    @Test
    void assignCustomersToGroup_groupNotFound_throwsException() {
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> mappingService.assignCustomersToGroup(List.of(1L), 99L));
    }

    @Test
    void unassignCustomersFromGroup_removesGroup() {
        testCustomer.setGroup(testGroup);
        when(customerRepository.findByIdIn(List.of(1L))).thenReturn(List.of(testCustomer));
        when(customerRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Customer> result = mappingService.unassignCustomersFromGroup(List.of(1L));
        assertNull(result.get(0).getGroup());
    }

    @Test
    void assignVehiclesToCustomer_setsCustomerOnVehicles() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepository.findByIdIn(List.of(1L))).thenReturn(List.of(testVehicle));
        when(vehicleRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Vehicle> result = mappingService.assignVehiclesToCustomer(List.of(1L), 1L);
        assertEquals(testCustomer, result.get(0).getCustomer());
    }

    @Test
    void assignVehiclesToCustomer_customerNotFound_throwsException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class,
                () -> mappingService.assignVehiclesToCustomer(List.of(1L), 99L));
    }

    @Test
    void unassignVehiclesFromCustomer_removesCustomer() {
        testVehicle.setCustomer(testCustomer);
        when(vehicleRepository.findByIdIn(List.of(1L))).thenReturn(List.of(testVehicle));
        when(invoiceBillRepository.countByVehicleIdAndCustomerIdAndPaymentStatus(1L, 1L, "NOT_PAID")).thenReturn(0L);
        when(vehicleRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<Vehicle> result = mappingService.unassignVehiclesFromCustomer(List.of(1L));
        assertNull(result.get(0).getCustomer());
    }
}
