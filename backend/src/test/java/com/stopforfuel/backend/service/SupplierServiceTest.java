package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Supplier;
import com.stopforfuel.backend.repository.SupplierRepository;
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
class SupplierServiceTest {

    @Mock
    private SupplierRepository repository;

    @InjectMocks
    private SupplierService supplierService;

    private Supplier testSupplier;

    @BeforeEach
    void setUp() {
        testSupplier = new Supplier();
        testSupplier.setId(1L);
        testSupplier.setName("Indian Oil Corp");
        testSupplier.setContactPerson("Raj");
        testSupplier.setPhone("9876543210");
        testSupplier.setEmail("raj@iocl.com");
        testSupplier.setActive(true);
        testSupplier.setScid(1L);
    }

    @Test
    void getAllSuppliers_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testSupplier));
        assertEquals(1, supplierService.getAllSuppliers().size());
    }

    @Test
    void getActiveSuppliers_returnsActive() {
        when(repository.findByActiveTrue()).thenReturn(List.of(testSupplier));
        assertEquals(1, supplierService.getActiveSuppliers().size());
    }

    @Test
    void getSupplierById_exists_returnsSupplier() {
        when(repository.findById(1L)).thenReturn(Optional.of(testSupplier));
        assertEquals("Indian Oil Corp", supplierService.getSupplierById(1L).getName());
    }

    @Test
    void getSupplierById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> supplierService.getSupplierById(99L));
    }

    @Test
    void toggleStatus_activeToInactive() {
        when(repository.findById(1L)).thenReturn(Optional.of(testSupplier));
        when(repository.save(any(Supplier.class))).thenAnswer(i -> i.getArgument(0));

        assertFalse(supplierService.toggleStatus(1L).isActive());
    }

    @Test
    void createSupplier_withoutScid_defaultsTo1() {
        Supplier s = new Supplier();
        s.setName("New Supplier");
        when(repository.save(any(Supplier.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1L, supplierService.createSupplier(s).getScid());
    }

    @Test
    void updateSupplier_updatesFields() {
        Supplier details = new Supplier();
        details.setName("Updated Name");
        details.setContactPerson("Kumar");
        details.setPhone("1234567890");
        details.setEmail("kumar@iocl.com");
        details.setActive(false);

        when(repository.findById(1L)).thenReturn(Optional.of(testSupplier));
        when(repository.save(any(Supplier.class))).thenAnswer(i -> i.getArgument(0));

        Supplier result = supplierService.updateSupplier(1L, details);
        assertEquals("Updated Name", result.getName());
        assertEquals("Kumar", result.getContactPerson());
        assertFalse(result.isActive());
    }

    @Test
    void deleteSupplier_callsRepository() {
        supplierService.deleteSupplier(1L);
        verify(repository).deleteById(1L);
    }
}
