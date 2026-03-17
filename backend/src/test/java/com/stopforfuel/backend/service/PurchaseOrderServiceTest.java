package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ReceiveItemDTO;
import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.PurchaseOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository repository;

    @Mock
    private GodownStockRepository godownStockRepository;

    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private PurchaseOrder testOrder;
    private Product testProduct;
    private Supplier testSupplier;
    private PurchaseOrderItem testItem;

    @BeforeEach
    void setUp() {
        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Engine Oil");

        testSupplier = new Supplier();
        testSupplier.setId(1L);
        testSupplier.setName("Oil Corp");

        testItem = new PurchaseOrderItem();
        testItem.setId(1L);
        testItem.setProduct(testProduct);
        testItem.setOrderedQty(100.0);
        testItem.setReceivedQty(0.0);
        testItem.setUnitPrice(new BigDecimal("250.00"));
        testItem.setTotalPrice(new BigDecimal("25000.00"));

        testOrder = new PurchaseOrder();
        testOrder.setId(1L);
        testOrder.setScid(1L);
        testOrder.setSupplier(testSupplier);
        testOrder.setOrderDate(LocalDate.of(2026, 3, 10));
        testOrder.setExpectedDeliveryDate(LocalDate.of(2026, 3, 20));
        testOrder.setStatus("DRAFT");
        testOrder.setTotalAmount(new BigDecimal("25000.00"));
        testOrder.setItems(new ArrayList<>(List.of(testItem)));
        testItem.setPurchaseOrder(testOrder);
    }

    @Test
    void getAll_returnsList() {
        when(repository.findByScidOrderByOrderDateDesc(1L)).thenReturn(List.of(testOrder));

        List<PurchaseOrder> result = purchaseOrderService.getAll();

        assertEquals(1, result.size());
        assertEquals("DRAFT", result.get(0).getStatus());
    }

    @Test
    void getById_exists_returnsOrder() {
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));

        PurchaseOrder result = purchaseOrderService.getById(1L);

        assertEquals("DRAFT", result.getStatus());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void getById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseOrderService.getById(99L));
        assertTrue(ex.getMessage().contains("PurchaseOrder not found"));
    }

    @Test
    void save_setsDefaultsAndLinksItems() {
        PurchaseOrder newOrder = new PurchaseOrder();
        newOrder.setSupplier(testSupplier);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setProduct(testProduct);
        item.setOrderedQty(50.0);
        newOrder.setItems(new ArrayList<>(List.of(item)));

        when(repository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.save(newOrder);

        assertEquals(1L, result.getScid());
        assertNotNull(result.getOrderDate());
        assertEquals("DRAFT", result.getStatus());
        assertEquals(result, item.getPurchaseOrder());
    }

    @Test
    void save_preservesExistingValues() {
        testOrder.setStatus("ORDERED");
        testOrder.setOrderDate(LocalDate.of(2026, 3, 5));

        when(repository.save(any(PurchaseOrder.class))).thenReturn(testOrder);

        PurchaseOrder result = purchaseOrderService.save(testOrder);

        assertEquals("ORDERED", result.getStatus());
        assertEquals(LocalDate.of(2026, 3, 5), result.getOrderDate());
    }

    @Test
    void update_draftOrder_updatesAllFields() {
        PurchaseOrder updated = new PurchaseOrder();
        updated.setSupplier(testSupplier);
        updated.setOrderDate(LocalDate.of(2026, 3, 12));
        updated.setExpectedDeliveryDate(LocalDate.of(2026, 3, 25));
        updated.setTotalAmount(new BigDecimal("30000.00"));
        updated.setRemarks("Updated order");
        PurchaseOrderItem newItem = new PurchaseOrderItem();
        newItem.setProduct(testProduct);
        newItem.setOrderedQty(120.0);
        updated.setItems(new ArrayList<>(List.of(newItem)));

        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(repository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.update(1L, updated);

        assertEquals(LocalDate.of(2026, 3, 12), result.getOrderDate());
        assertEquals(new BigDecimal("30000.00"), result.getTotalAmount());
        assertEquals("Updated order", result.getRemarks());
        assertEquals(1, result.getItems().size());
        assertEquals(result, result.getItems().get(0).getPurchaseOrder());
    }

    @Test
    void update_nonDraftOrder_throwsException() {
        testOrder.setStatus("ORDERED");
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseOrderService.update(1L, new PurchaseOrder()));
        assertTrue(ex.getMessage().contains("Can only edit purchase orders in DRAFT status"));
    }

    @Test
    void receiveDelivery_fullReceive_statusReceived() {
        testOrder.setStatus("ORDERED");
        GodownStock godown = new GodownStock();
        godown.setProduct(testProduct);
        godown.setCurrentStock(50.0);
        godown.setScid(1L);

        ReceiveItemDTO dto = new ReceiveItemDTO();
        dto.setItemId(1L);
        dto.setReceivedQty(100.0);

        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(godown));
        when(repository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.receiveDelivery(1L, List.of(dto));

        assertEquals("RECEIVED", result.getStatus());
        assertEquals(100.0, testItem.getReceivedQty());
        assertEquals(150.0, godown.getCurrentStock());
        verify(godownStockRepository).save(godown);
    }

    @Test
    void receiveDelivery_partialReceive_statusPartiallyReceived() {
        testOrder.setStatus("ORDERED");
        GodownStock godown = new GodownStock();
        godown.setProduct(testProduct);
        godown.setCurrentStock(50.0);
        godown.setScid(1L);

        ReceiveItemDTO dto = new ReceiveItemDTO();
        dto.setItemId(1L);
        dto.setReceivedQty(40.0);

        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.of(godown));
        when(repository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.receiveDelivery(1L, List.of(dto));

        assertEquals("PARTIALLY_RECEIVED", result.getStatus());
        assertEquals(40.0, testItem.getReceivedQty());
        assertEquals(90.0, godown.getCurrentStock());
    }

    @Test
    void receiveDelivery_autoCreatesGodownStock() {
        testOrder.setStatus("ORDERED");

        ReceiveItemDTO dto = new ReceiveItemDTO();
        dto.setItemId(1L);
        dto.setReceivedQty(100.0);

        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(godownStockRepository.findByProductIdAndScid(1L, 1L)).thenReturn(Optional.empty());
        when(godownStockRepository.save(any(GodownStock.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.receiveDelivery(1L, List.of(dto));

        assertEquals("RECEIVED", result.getStatus());
        verify(godownStockRepository).save(argThat(gs ->
                gs.getCurrentStock() == 100.0 && gs.getScid() == 1L));
    }

    @Test
    void receiveDelivery_cancelledOrder_throwsException() {
        testOrder.setStatus("CANCELLED");
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseOrderService.receiveDelivery(1L, List.of()));
        assertTrue(ex.getMessage().contains("Cannot receive delivery"));
    }

    @Test
    void receiveDelivery_receivedOrder_throwsException() {
        testOrder.setStatus("RECEIVED");
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseOrderService.receiveDelivery(1L, List.of()));
        assertTrue(ex.getMessage().contains("Cannot receive delivery"));
    }

    @Test
    void cancel_draftOrder_success() {
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(repository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.cancel(1L);

        assertEquals("CANCELLED", result.getStatus());
    }

    @Test
    void cancel_orderedOrder_success() {
        testOrder.setStatus("ORDERED");
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(repository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.cancel(1L);

        assertEquals("CANCELLED", result.getStatus());
    }

    @Test
    void cancel_receivedOrder_throwsException() {
        testOrder.setStatus("RECEIVED");
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseOrderService.cancel(1L));
        assertTrue(ex.getMessage().contains("Can only cancel DRAFT or ORDERED"));
    }

    @Test
    void cancel_partiallyReceivedOrder_throwsException() {
        testOrder.setStatus("PARTIALLY_RECEIVED");
        when(repository.findById(1L)).thenReturn(Optional.of(testOrder));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseOrderService.cancel(1L));
        assertTrue(ex.getMessage().contains("Can only cancel DRAFT or ORDERED"));
    }
}
