package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ReceiveItemDTO;
import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.entity.PurchaseOrderItem;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository repository;
    private final GodownStockRepository godownStockRepository;

    public List<PurchaseOrder> getAll() {
        return repository.findByScidOrderByOrderDateDesc(1L);
    }

    public PurchaseOrder getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("PurchaseOrder not found with id: " + id));
    }

    public List<PurchaseOrder> getByStatus(String status) {
        return repository.findByStatusAndScid(status, 1L);
    }

    public List<PurchaseOrder> getBySupplier(Long supplierId) {
        return repository.findBySupplierIdAndScid(supplierId, 1L);
    }

    public PurchaseOrder save(PurchaseOrder order) {
        if (order.getScid() == null) order.setScid(1L);
        if (order.getOrderDate() == null) order.setOrderDate(LocalDate.now());
        if (order.getStatus() == null) order.setStatus("DRAFT");
        // Link items back to order
        if (order.getItems() != null) {
            for (PurchaseOrderItem item : order.getItems()) {
                item.setPurchaseOrder(order);
            }
        }
        return repository.save(order);
    }

    public PurchaseOrder update(Long id, PurchaseOrder details) {
        PurchaseOrder existing = getById(id);
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new RuntimeException("Can only edit purchase orders in DRAFT status");
        }
        existing.setSupplier(details.getSupplier());
        existing.setOrderDate(details.getOrderDate());
        existing.setExpectedDeliveryDate(details.getExpectedDeliveryDate());
        existing.setTotalAmount(details.getTotalAmount());
        existing.setRemarks(details.getRemarks());
        // Update items
        existing.getItems().clear();
        if (details.getItems() != null) {
            for (PurchaseOrderItem item : details.getItems()) {
                item.setPurchaseOrder(existing);
                existing.getItems().add(item);
            }
        }
        return repository.save(existing);
    }

    @Transactional
    public PurchaseOrder receiveDelivery(Long id, List<ReceiveItemDTO> receivedItems) {
        PurchaseOrder order = getById(id);
        if ("CANCELLED".equals(order.getStatus()) || "RECEIVED".equals(order.getStatus())) {
            throw new RuntimeException("Cannot receive delivery for a " + order.getStatus() + " order");
        }

        for (ReceiveItemDTO dto : receivedItems) {
            PurchaseOrderItem item = order.getItems().stream()
                    .filter(i -> i.getId().equals(dto.getItemId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Item not found: " + dto.getItemId()));

            double newReceived = (item.getReceivedQty() != null ? item.getReceivedQty() : 0.0) + dto.getReceivedQty();
            item.setReceivedQty(newReceived);

            // Update godown stock
            GodownStock godown = godownStockRepository.findByProductIdAndScid(item.getProduct().getId(), 1L)
                    .orElseGet(() -> {
                        GodownStock gs = new GodownStock();
                        gs.setProduct(item.getProduct());
                        gs.setCurrentStock(0.0);
                        gs.setReorderLevel(0.0);
                        gs.setMaxStock(0.0);
                        gs.setScid(1L);
                        return gs;
                    });
            godown.setCurrentStock(godown.getCurrentStock() + dto.getReceivedQty());
            godown.setLastRestockDate(LocalDate.now());
            godownStockRepository.save(godown);
        }

        // Auto-update status
        boolean allReceived = order.getItems().stream()
                .allMatch(i -> i.getReceivedQty() != null && i.getReceivedQty() >= i.getOrderedQty());
        boolean anyReceived = order.getItems().stream()
                .anyMatch(i -> i.getReceivedQty() != null && i.getReceivedQty() > 0);

        if (allReceived) {
            order.setStatus("RECEIVED");
        } else if (anyReceived) {
            order.setStatus("PARTIALLY_RECEIVED");
        }

        return repository.save(order);
    }

    public PurchaseOrder cancel(Long id) {
        PurchaseOrder order = getById(id);
        if (!"DRAFT".equals(order.getStatus()) && !"ORDERED".equals(order.getStatus())) {
            throw new RuntimeException("Can only cancel DRAFT or ORDERED purchase orders");
        }
        order.setStatus("CANCELLED");
        return repository.save(order);
    }
}
