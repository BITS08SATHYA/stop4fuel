package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ReceiveItemDTO;
import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.entity.PurchaseOrderItem;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.GodownStockRepository;
import com.stopforfuel.backend.repository.PurchaseOrderRepository;
import com.stopforfuel.config.SecurityUtils;
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

    @Transactional(readOnly = true)
    public List<PurchaseOrder> getAll() {
        return repository.findByScidOrderByOrderDateDesc(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> getByStatus(String status) {
        return repository.findByStatusAndScid(status, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> getBySupplier(Long supplierId) {
        return repository.findBySupplierIdAndScid(supplierId, SecurityUtils.getScid());
    }

    public PurchaseOrder save(PurchaseOrder order) {
        if (order.getScid() == null) order.setScid(SecurityUtils.getScid());
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
            throw new BusinessException("Can only edit purchase orders in DRAFT status");
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
            throw new BusinessException("Cannot receive delivery for a " + order.getStatus() + " order");
        }

        for (ReceiveItemDTO dto : receivedItems) {
            PurchaseOrderItem item = order.getItems().stream()
                    .filter(i -> i.getId().equals(dto.getItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + dto.getItemId()));

            double newReceived = (item.getReceivedQty() != null ? item.getReceivedQty() : 0.0) + dto.getReceivedQty();
            item.setReceivedQty(newReceived);

            // Update godown stock
            GodownStock godown = godownStockRepository.findByProductIdAndScid(item.getProduct().getId(), SecurityUtils.getScid())
                    .orElseGet(() -> {
                        GodownStock gs = new GodownStock();
                        gs.setProduct(item.getProduct());
                        gs.setCurrentStock(0.0);
                        gs.setReorderLevel(0.0);
                        gs.setMaxStock(0.0);
                        gs.setScid(SecurityUtils.getScid());
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
            throw new BusinessException("Can only cancel DRAFT or ORDERED purchase orders");
        }
        order.setStatus("CANCELLED");
        return repository.save(order);
    }
}
