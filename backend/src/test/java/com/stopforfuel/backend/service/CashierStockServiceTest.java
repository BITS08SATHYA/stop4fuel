package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.repository.CashierStockRepository;
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
class CashierStockServiceTest {

    @Mock
    private CashierStockRepository repository;

    @InjectMocks
    private CashierStockService cashierStockService;

    private CashierStock testStock;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Engine Oil");

        testStock = new CashierStock();
        testStock.setId(1L);
        testStock.setScid(1L);
        testStock.setProduct(testProduct);
        testStock.setCurrentStock(25.0);
        testStock.setMaxCapacity(50.0);
    }

    @Test
    void getAll_returnsList() {
        when(repository.findByScid(1L)).thenReturn(List.of(testStock));

        List<CashierStock> result = cashierStockService.getAll();

        assertEquals(1, result.size());
        assertEquals("Engine Oil", result.get(0).getProduct().getName());
    }

    @Test
    void getById_exists_returnsStock() {
        when(repository.findById(1L)).thenReturn(Optional.of(testStock));

        CashierStock result = cashierStockService.getById(1L);

        assertEquals(25.0, result.getCurrentStock());
        assertEquals(50.0, result.getMaxCapacity());
    }

    @Test
    void getById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> cashierStockService.getById(99L));
        assertTrue(ex.getMessage().contains("CashierStock not found"));
    }

    @Test
    void save_setsScidAndReturns() {
        CashierStock newStock = new CashierStock();
        newStock.setProduct(testProduct);
        newStock.setCurrentStock(10.0);

        when(repository.save(any(CashierStock.class))).thenReturn(testStock);

        CashierStock result = cashierStockService.save(newStock);

        assertEquals(1L, newStock.getScid());
        verify(repository).save(newStock);
    }

    @Test
    void save_withExistingScid_doesNotOverride() {
        testStock.setScid(2L);
        when(repository.save(any(CashierStock.class))).thenReturn(testStock);

        CashierStock result = cashierStockService.save(testStock);

        assertEquals(2L, testStock.getScid());
        verify(repository).save(testStock);
    }

    @Test
    void update_updatesAllFields() {
        CashierStock updated = new CashierStock();
        updated.setProduct(testProduct);
        updated.setCurrentStock(40.0);
        updated.setMaxCapacity(100.0);

        when(repository.findById(1L)).thenReturn(Optional.of(testStock));
        when(repository.save(any(CashierStock.class))).thenAnswer(i -> i.getArgument(0));

        CashierStock result = cashierStockService.update(1L, updated);

        assertEquals(40.0, result.getCurrentStock());
        assertEquals(100.0, result.getMaxCapacity());
    }

    @Test
    void delete_callsDeleteById() {
        cashierStockService.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void getByProduct_exists_returnsStock() {
        when(repository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testStock));

        CashierStock result = cashierStockService.getByProduct(1L);

        assertNotNull(result);
        assertEquals(25.0, result.getCurrentStock());
    }

    @Test
    void getByProduct_notExists_returnsNull() {
        when(repository.findByProductIdAndScid(99L, 1L)).thenReturn(Optional.empty());

        CashierStock result = cashierStockService.getByProduct(99L);

        assertNull(result);
    }
}
