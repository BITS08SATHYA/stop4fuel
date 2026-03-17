package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.repository.GodownStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GodownStockServiceTest {

    @Mock
    private GodownStockRepository repository;

    @InjectMocks
    private GodownStockService godownStockService;

    private GodownStock testStock;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Engine Oil");

        testStock = new GodownStock();
        testStock.setId(1L);
        testStock.setScid(1L);
        testStock.setProduct(testProduct);
        testStock.setCurrentStock(100.0);
        testStock.setReorderLevel(20.0);
        testStock.setMaxStock(500.0);
        testStock.setLocation("Rack A");
        testStock.setLastRestockDate(LocalDate.of(2026, 3, 10));
    }

    @Test
    void getAll_returnsList() {
        when(repository.findByScid(1L)).thenReturn(List.of(testStock));

        List<GodownStock> result = godownStockService.getAll();

        assertEquals(1, result.size());
        assertEquals("Engine Oil", result.get(0).getProduct().getName());
    }

    @Test
    void getById_exists_returnsStock() {
        when(repository.findById(1L)).thenReturn(Optional.of(testStock));

        GodownStock result = godownStockService.getById(1L);

        assertEquals(100.0, result.getCurrentStock());
        assertEquals("Rack A", result.getLocation());
    }

    @Test
    void getById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> godownStockService.getById(99L));
        assertTrue(ex.getMessage().contains("GodownStock not found"));
    }

    @Test
    void save_setsScidAndReturns() {
        GodownStock newStock = new GodownStock();
        newStock.setProduct(testProduct);
        newStock.setCurrentStock(50.0);

        when(repository.save(any(GodownStock.class))).thenReturn(testStock);

        GodownStock result = godownStockService.save(newStock);

        assertEquals(1L, newStock.getScid());
        verify(repository).save(newStock);
    }

    @Test
    void save_withExistingScid_doesNotOverride() {
        testStock.setScid(2L);
        when(repository.save(any(GodownStock.class))).thenReturn(testStock);

        GodownStock result = godownStockService.save(testStock);

        assertEquals(2L, testStock.getScid());
        verify(repository).save(testStock);
    }

    @Test
    void update_updatesAllFields() {
        GodownStock updated = new GodownStock();
        updated.setProduct(testProduct);
        updated.setCurrentStock(200.0);
        updated.setReorderLevel(30.0);
        updated.setMaxStock(600.0);
        updated.setLocation("Rack B");
        updated.setLastRestockDate(LocalDate.of(2026, 3, 15));

        when(repository.findById(1L)).thenReturn(Optional.of(testStock));
        when(repository.save(any(GodownStock.class))).thenAnswer(i -> i.getArgument(0));

        GodownStock result = godownStockService.update(1L, updated);

        assertEquals(200.0, result.getCurrentStock());
        assertEquals(30.0, result.getReorderLevel());
        assertEquals(600.0, result.getMaxStock());
        assertEquals("Rack B", result.getLocation());
        assertEquals(LocalDate.of(2026, 3, 15), result.getLastRestockDate());
    }

    @Test
    void delete_callsDeleteById() {
        godownStockService.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void getLowStockItems_returnsLowStockList() {
        GodownStock lowStock = new GodownStock();
        lowStock.setId(2L);
        lowStock.setCurrentStock(10.0);
        lowStock.setReorderLevel(20.0);

        when(repository.findLowStockItems(1L)).thenReturn(List.of(lowStock));

        List<GodownStock> result = godownStockService.getLowStockItems();

        assertEquals(1, result.size());
        assertTrue(result.get(0).getCurrentStock() <= result.get(0).getReorderLevel());
    }

    @Test
    void getByProduct_exists_returnsStock() {
        when(repository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testStock));

        GodownStock result = godownStockService.getByProduct(1L);

        assertNotNull(result);
        assertEquals(100.0, result.getCurrentStock());
    }

    @Test
    void getByProduct_notExists_returnsNull() {
        when(repository.findByProductIdAndScid(99L, 1L)).thenReturn(Optional.empty());

        GodownStock result = godownStockService.getByProduct(99L);

        assertNull(result);
    }
}
