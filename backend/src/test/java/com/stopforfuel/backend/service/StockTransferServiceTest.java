package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.CashierStockRepository;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.ProductInventoryRepository;
import com.stopforfuel.backend.repository.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository repository;

    @Mock
    private GodownStockRepository godownStockRepository;

    @Mock
    private CashierStockRepository cashierStockRepository;

    @Mock
    private ProductInventoryRepository productInventoryRepository;

    @Mock
    private ShiftService shiftService;

    @InjectMocks
    private StockTransferService stockTransferService;

    private Product testProduct;
    private GodownStock testGodownStock;
    private CashierStock testCashierStock;
    private StockTransfer testTransfer;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Engine Oil");

        testGodownStock = new GodownStock();
        testGodownStock.setId(1L);
        testGodownStock.setProduct(testProduct);
        testGodownStock.setCurrentStock(100.0);
        testGodownStock.setScid(1L);

        testCashierStock = new CashierStock();
        testCashierStock.setId(1L);
        testCashierStock.setProduct(testProduct);
        testCashierStock.setCurrentStock(30.0);
        testCashierStock.setMaxCapacity(50.0);
        testCashierStock.setScid(1L);

        testTransfer = new StockTransfer();
        testTransfer.setId(1L);
        testTransfer.setProduct(testProduct);
        testTransfer.setQuantity(10.0);
        testTransfer.setFromLocation("GODOWN");
        testTransfer.setToLocation("CASHIER");
        testTransfer.setTransferDate(LocalDateTime.of(2026, 3, 15, 10, 0));
        testTransfer.setScid(1L);
    }

    @Test
    void getAll_returnsList() {
        when(repository.findByScidOrderByTransferDateDesc(1L)).thenReturn(List.of(testTransfer));

        List<StockTransfer> result = stockTransferService.getAll();

        assertEquals(1, result.size());
        assertEquals("GODOWN", result.get(0).getFromLocation());
    }

    @Test
    void createTransfer_godownToCashier_success() {
        when(shiftService.getActiveShift()).thenReturn(null);
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testGodownStock));
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testCashierStock));
        when(repository.save(any(StockTransfer.class))).thenReturn(testTransfer);

        StockTransfer result = stockTransferService.createTransfer(testTransfer);

        assertNotNull(result);
        assertEquals(90.0, testGodownStock.getCurrentStock());
        assertEquals(40.0, testCashierStock.getCurrentStock());
        verify(godownStockRepository).save(testGodownStock);
        verify(cashierStockRepository).save(testCashierStock);
    }

    @Test
    void createTransfer_godownToCashier_updatesProductInventory() {
        Shift activeShift = new Shift();
        activeShift.setId(5L);
        when(shiftService.getActiveShift()).thenReturn(activeShift);

        ProductInventory pi = new ProductInventory();
        pi.setOpenStock(20.0);
        pi.setIncomeStock(0.0);
        pi.setTotalStock(20.0);
        pi.setCloseStock(20.0);
        when(productInventoryRepository.findTopByShiftIdAndProductIdOrderByIdDesc(5L, 1L)).thenReturn(pi);

        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testGodownStock));
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testCashierStock));
        when(repository.save(any(StockTransfer.class))).thenReturn(testTransfer);

        stockTransferService.createTransfer(testTransfer);

        assertEquals(10.0, pi.getIncomeStock());
        assertEquals(30.0, pi.getTotalStock());
        verify(productInventoryRepository).save(pi);
    }

    @Test
    void createTransfer_cashierToGodown_success() {
        testTransfer.setFromLocation("CASHIER");
        testTransfer.setToLocation("GODOWN");
        testTransfer.setQuantity(5.0);

        when(shiftService.getActiveShift()).thenReturn(null);
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testCashierStock));
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testGodownStock));
        when(repository.save(any(StockTransfer.class))).thenReturn(testTransfer);

        StockTransfer result = stockTransferService.createTransfer(testTransfer);

        assertNotNull(result);
        assertEquals(25.0, testCashierStock.getCurrentStock());
        assertEquals(105.0, testGodownStock.getCurrentStock());
        verify(cashierStockRepository).save(testCashierStock);
        verify(godownStockRepository).save(testGodownStock);
    }

    @Test
    void createTransfer_godownToCashier_insufficientStock_throwsException() {
        testTransfer.setQuantity(200.0);

        when(shiftService.getActiveShift()).thenReturn(null);
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testGodownStock));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stockTransferService.createTransfer(testTransfer));
        assertTrue(ex.getMessage().contains("Insufficient godown stock"));
    }

    @Test
    void createTransfer_cashierToGodown_insufficientStock_throwsException() {
        testTransfer.setFromLocation("CASHIER");
        testTransfer.setToLocation("GODOWN");
        testTransfer.setQuantity(50.0);

        when(shiftService.getActiveShift()).thenReturn(null);
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testCashierStock));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stockTransferService.createTransfer(testTransfer));
        assertTrue(ex.getMessage().contains("Insufficient cashier stock"));
    }

    @Test
    void createTransfer_godownToCashier_autoCreatesCashierStock() {
        when(shiftService.getActiveShift()).thenReturn(null);
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testGodownStock));
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.empty());
        when(cashierStockRepository.save(any(CashierStock.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(StockTransfer.class))).thenReturn(testTransfer);

        StockTransfer result = stockTransferService.createTransfer(testTransfer);

        assertNotNull(result);
        assertEquals(90.0, testGodownStock.getCurrentStock());
        verify(cashierStockRepository).save(argThat(cs ->
                cs.getCurrentStock() == 10.0 && cs.getScid() == 1L));
    }

    @Test
    void createTransfer_cashierToGodown_autoCreatesGodownStock() {
        testTransfer.setFromLocation("CASHIER");
        testTransfer.setToLocation("GODOWN");
        testTransfer.setQuantity(5.0);

        when(shiftService.getActiveShift()).thenReturn(null);
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testCashierStock));
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.empty());
        when(godownStockRepository.save(any(GodownStock.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(StockTransfer.class))).thenReturn(testTransfer);

        StockTransfer result = stockTransferService.createTransfer(testTransfer);

        assertNotNull(result);
        assertEquals(25.0, testCashierStock.getCurrentStock());
        verify(godownStockRepository).save(argThat(gs ->
                gs.getCurrentStock() == 5.0 && gs.getScid() == 1L));
    }

    @Test
    void createTransfer_setsScidWhenNull() {
        testTransfer.setScid(null);
        testTransfer.setTransferDate(null);

        when(shiftService.getActiveShift()).thenReturn(null);
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testGodownStock));
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testCashierStock));
        when(repository.save(any(StockTransfer.class))).thenReturn(testTransfer);

        stockTransferService.createTransfer(testTransfer);

        assertEquals(1L, testTransfer.getScid());
        assertNotNull(testTransfer.getTransferDate());
    }

    @Test
    void createTransfer_godownNotFound_throwsException() {
        when(shiftService.getActiveShift()).thenReturn(null);
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> stockTransferService.createTransfer(testTransfer));
        assertTrue(ex.getMessage().contains("No godown stock found"));
    }

    @Test
    void createTransfer_linksToActiveShift() {
        Shift activeShift = new Shift();
        activeShift.setId(7L);
        when(shiftService.getActiveShift()).thenReturn(activeShift);

        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testGodownStock));
        when(cashierStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(testCashierStock));
        when(repository.save(any(StockTransfer.class))).thenReturn(testTransfer);

        stockTransferService.createTransfer(testTransfer);

        assertEquals(7L, testTransfer.getShiftId());
    }
}
