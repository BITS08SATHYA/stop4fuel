package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.entity.ManualStockReceipt;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.ManualStockReceiptRepository;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductInventoryManualStockTest {

    @Mock private ProductInventoryRepository repository;
    @Mock private ProductRepository productRepository;
    @Mock private CashierStockRepository cashierStockRepository;
    @Mock private ShiftService shiftService;
    @Mock private ManualStockReceiptRepository manualStockReceiptRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ProductInventoryService service;

    private Shift shift;
    private Product oil;

    @BeforeEach
    void setUp() {
        shift = new Shift();
        shift.setId(2700L);
        shift.setScid(1L);

        oil = new Product();
        oil.setId(40L);
        oil.setName("2T Oil 1L");
        oil.setCategory("LUBRICANT");
        oil.setUnit("PCS");
        oil.setPrice(new BigDecimal("350"));
    }

    @Test
    void addsStockToShiftRowAndCounterAndRecordsWhoAndWhy() {
        ProductInventory row = new ProductInventory();
        row.setProduct(oil);
        row.setOpenStock(2.0);
        row.setIncomeStock(0.0);
        row.setTotalStock(2.0);
        row.setCloseStock(1.0); // one sold this shift
        row.setSales(1.0);
        CashierStock counter = new CashierStock();
        counter.setCurrentStock(1.0);

        when(shiftService.getActiveShift()).thenReturn(shift);
        when(productRepository.findByIdAndScid(40L, 1L)).thenReturn(Optional.of(oil));
        when(repository.findByProductIdAndShiftIdForUpdate(40L, 2700L)).thenReturn(row);
        when(cashierStockRepository.findByProductIdAndScidForUpdate(40L, 1L)).thenReturn(Optional.of(counter));
        when(manualStockReceiptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ManualStockReceipt receipt = service.addManualStock(40L, 5.0, "  carton from godown ");

        assertEquals(5.0, row.getIncomeStock());
        assertEquals(7.0, row.getTotalStock());
        assertEquals(6.0, row.getCloseStock());
        assertEquals(1.0, row.getSales(), "adding stock must not change what was sold");
        assertEquals(6.0, counter.getCurrentStock());
        assertEquals(2700L, receipt.getShiftId());
        assertEquals(5.0, receipt.getQuantity());
        assertEquals("carton from godown", receipt.getReason());
    }

    @Test
    void createsShiftRowForProductActivatedMidShift() {
        CashierStock counter = new CashierStock();
        counter.setCurrentStock(0.0);

        when(shiftService.getActiveShift()).thenReturn(shift);
        when(productRepository.findByIdAndScid(40L, 1L)).thenReturn(Optional.of(oil));
        when(repository.findByProductIdAndShiftIdForUpdate(40L, 2700L)).thenReturn(null);
        when(cashierStockRepository.findByProductIdAndScid(40L, 1L)).thenReturn(Optional.of(counter));
        when(cashierStockRepository.findByProductIdAndScidForUpdate(40L, 1L)).thenReturn(Optional.of(counter));
        when(manualStockReceiptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.addManualStock(40L, 3.0, "new product");

        ArgumentCaptor<ProductInventory> saved = ArgumentCaptor.forClass(ProductInventory.class);
        verify(repository).save(saved.capture());
        assertEquals(2700L, saved.getValue().getShiftId());
        assertEquals(3.0, saved.getValue().getCloseStock());
        assertEquals(0.0, saved.getValue().getSales());
    }

    @Test
    void refusesFuel() {
        oil.setCategory("FUEL");
        when(shiftService.getActiveShift()).thenReturn(shift);
        when(productRepository.findByIdAndScid(40L, 1L)).thenReturn(Optional.of(oil));

        assertThrows(BusinessException.class, () -> service.addManualStock(40L, 100.0, "tanker"));
        verify(manualStockReceiptRepository, never()).save(any());
    }

    @Test
    void refusesWithoutReasonOrPositiveQuantityOrOpenShift() {
        assertThrows(BusinessException.class, () -> service.addManualStock(40L, 5.0, " "));
        assertThrows(BusinessException.class, () -> service.addManualStock(40L, 0.0, "x"));
        when(shiftService.getActiveShift()).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.addManualStock(40L, 5.0, "x"));
        verify(manualStockReceiptRepository, never()).save(any());
    }
}
