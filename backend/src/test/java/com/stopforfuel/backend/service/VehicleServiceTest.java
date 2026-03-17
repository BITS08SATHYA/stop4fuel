package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private Vehicle testVehicle;
    private Customer testCustomer;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");
        testCustomer.setCreditLimitLiters(new BigDecimal("5000"));

        testVehicle = new Vehicle();
        testVehicle.setId(1L);
        testVehicle.setVehicleNumber("TN01AB1234");
        testVehicle.setStatus("ACTIVE");
        testVehicle.setConsumedLiters(BigDecimal.ZERO);
        testVehicle.setCustomer(testCustomer);
    }

    @Test
    void getAllVehicles_noSearch_returnsAll() {
        when(vehicleRepository.findAll()).thenReturn(List.of(testVehicle));
        assertEquals(1, vehicleService.getAllVehicles(null).size());
    }

    @Test
    void getAllVehicles_withSearch_filtersResults() {
        when(vehicleRepository.findByVehicleNumberContainingIgnoreCase("TN01"))
                .thenReturn(List.of(testVehicle));
        assertEquals(1, vehicleService.getAllVehicles("TN01").size());
    }

    @Test
    void getAllVehicles_emptySearch_returnsAll() {
        when(vehicleRepository.findAll()).thenReturn(List.of(testVehicle));
        assertEquals(1, vehicleService.getAllVehicles("").size());
    }

    @Test
    void getVehiclesByCustomerId_returnsList() {
        when(vehicleRepository.findByCustomerId(1L)).thenReturn(List.of(testVehicle));
        assertEquals(1, vehicleService.getVehiclesByCustomerId(1L).size());
    }

    @Test
    void createVehicle_setsDefaults() {
        Vehicle v = new Vehicle();
        v.setVehicleNumber("TN02CD5678");
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle result = vehicleService.createVehicle(v);
        assertEquals("ACTIVE", result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getConsumedLiters());
    }

    @Test
    void createVehicle_withLiterLimit_validatesAgainstCustomerLimit() {
        testVehicle.setMaxLitersPerMonth(new BigDecimal("3000"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepository.findByCustomerId(1L)).thenReturn(List.of());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle result = vehicleService.createVehicle(testVehicle);
        assertEquals(new BigDecimal("3000"), result.getMaxLitersPerMonth());
    }

    @Test
    void createVehicle_exceedsCustomerLimit_throwsException() {
        testVehicle.setMaxLitersPerMonth(new BigDecimal("6000"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepository.findByCustomerId(1L)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> vehicleService.createVehicle(testVehicle));
    }

    @Test
    void updateVehicle_updatesFields() {
        Vehicle details = new Vehicle();
        details.setVehicleNumber("TN99ZZ9999");
        details.setCustomer(testCustomer);

        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle result = vehicleService.updateVehicle(1L, details);
        assertEquals("TN99ZZ9999", result.getVehicleNumber());
    }

    @Test
    void deleteVehicle_callsRepository() {
        vehicleService.deleteVehicle(1L);
        verify(vehicleRepository).deleteById(1L);
    }

    @Test
    void toggleStatus_activeToInactive() {
        testVehicle.setStatus("ACTIVE");
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("INACTIVE", vehicleService.toggleStatus(1L).getStatus());
    }

    @Test
    void toggleStatus_inactiveToActive() {
        testVehicle.setStatus("INACTIVE");
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("ACTIVE", vehicleService.toggleStatus(1L).getStatus());
    }

    @Test
    void toggleStatus_blockedToActive() {
        testVehicle.setStatus("BLOCKED");
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("ACTIVE", vehicleService.toggleStatus(1L).getStatus());
    }

    @Test
    void toggleStatus_nullToInactive() {
        testVehicle.setStatus(null);
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("INACTIVE", vehicleService.toggleStatus(1L).getStatus());
    }
}
