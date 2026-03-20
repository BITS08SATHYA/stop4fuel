package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import com.stopforfuel.backend.repository.ProductRepository;
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
class ProductInventoryServiceTest {

    @Mock
    private ProductInventoryRepository repository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CashierStockRepository cashierStockRepository;

    @Mock
    private ShiftService shiftService;

    @InjectMocks
    private ProductInventoryService productInventoryService;

    private ProductInventory testInventory;

    @BeforeEach
    void setUp() {
        testInventory = new ProductInventory();
        testInventory.setId(1L);
        testInventory.setDate(LocalDate.now());
        testInventory.setOpenStock(50.0);
        testInventory.setIncomeStock(20.0);
        testInventory.setCloseStock(55.0);
        testInventory.setScid(1L);
    }

    @Test
    void getAll_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testInventory));
        assertEquals(1, productInventoryService.getAll().size());
    }

    @Test
    void getByDate_returnsList() {
        LocalDate today = LocalDate.now();
        when(repository.findByDate(today)).thenReturn(List.of(testInventory));
        assertEquals(1, productInventoryService.getByDate(today).size());
    }

    @Test
    void getByProductId_returnsList() {
        when(repository.findByProductId(1L)).thenReturn(List.of(testInventory));
        assertEquals(1, productInventoryService.getByProductId(1L).size());
    }

    @Test
    void getById_exists_returnsInventory() {
        when(repository.findById(1L)).thenReturn(Optional.of(testInventory));
        assertNotNull(productInventoryService.getById(1L));
    }

    @Test
    void getById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> productInventoryService.getById(99L));
    }

    @Test
    void save_calculatesTotalAndSales() {
        when(repository.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));

        ProductInventory result = productInventoryService.save(testInventory);
        assertEquals(70.0, result.getTotalStock()); // 50 + 20
        assertEquals(15.0, result.getSales());       // 70 - 55
    }

    @Test
    void save_nullStocks_treatsAsZero() {
        ProductInventory inv = new ProductInventory();
        inv.setScid(1L);
        when(repository.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(0.0, productInventoryService.save(inv).getTotalStock());
    }

    @Test
    void save_assignsActiveShift() {
        testInventory.setShiftId(null);
        Shift shift = new Shift();
        shift.setId(7L);
        when(shiftService.getActiveShift()).thenReturn(shift);
        when(repository.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(7L, productInventoryService.save(testInventory).getShiftId());
    }

    @Test
    void update_updatesAndRecalculates() {
        ProductInventory details = new ProductInventory();
        details.setDate(LocalDate.now());
        details.setOpenStock(100.0);
        details.setIncomeStock(50.0);
        details.setCloseStock(120.0);

        when(repository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(repository.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));

        ProductInventory result = productInventoryService.update(1L, details);
        assertEquals(150.0, result.getTotalStock());
        assertEquals(30.0, result.getSales());
    }

    @Test
    void delete_callsRepository() {
        productInventoryService.delete(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void autoCreateForShift_usesCashierStockForOpenStock() {
        Shift shift = new Shift();
        shift.setId(10L);
        shift.setScid(1L);

        Product product = new Product();
        product.setId(1L);
        product.setName("Oil");
        product.setPrice(java.math.BigDecimal.valueOf(250));

        CashierStock cs = new CashierStock();
        cs.setCurrentStock(15.0);

        when(productRepository.findByActive(true)).thenReturn(List.of(product));
        when(repository.findByShiftId(10L)).thenReturn(List.of());
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(cs));
        when(repository.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));

        productInventoryService.autoCreateForShift(shift);

        verify(repository).save(argThat(pi -> pi.getOpenStock() == 15.0 && pi.getShiftId() == 10L));
    }

    @Test
    void autoCreateForShift_fallsToPreviousCloseStock_whenNoCashierStock() {
        Shift shift = new Shift();
        shift.setId(10L);
        shift.setScid(1L);

        Product product = new Product();
        product.setId(2L);
        product.setName("Coolant");
        product.setPrice(java.math.BigDecimal.valueOf(100));

        ProductInventory prev = new ProductInventory();
        prev.setCloseStock(8.0);

        when(productRepository.findByActive(true)).thenReturn(List.of(product));
        when(repository.findByShiftId(10L)).thenReturn(List.of());
        when(cashierStockRepository.findByProductIdAndScid(2L, 1L)).thenReturn(Optional.empty());
        when(repository.findTopByProductIdOrderByDateDescIdDesc(2L)).thenReturn(prev);
        when(repository.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));

        productInventoryService.autoCreateForShift(shift);

        verify(repository).save(argThat(pi -> pi.getOpenStock() == 8.0));
    }

    @Test
    void finalizeForShift_syncsCashierStock() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Oil");
        product.setPrice(java.math.BigDecimal.valueOf(250));

        ProductInventory pi = new ProductInventory();
        pi.setProduct(product);
        pi.setCloseStock(12.0);
        pi.setSales(3.0);

        CashierStock cs = new CashierStock();
        cs.setCurrentStock(20.0);

        when(repository.findByShiftId(10L)).thenReturn(List.of(pi));
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(cs));
        when(repository.save(any(ProductInventory.class))).thenAnswer(i -> i.getArgument(0));

        productInventoryService.finalizeForShift(10L);

        assertEquals(12.0, cs.getCurrentStock());
        verify(cashierStockRepository).save(cs);
        assertNotNull(pi.getRate());
        assertNotNull(pi.getAmount());
    }
}
