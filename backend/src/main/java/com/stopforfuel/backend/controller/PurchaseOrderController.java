package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ReceiveItemDTO;
import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PURCHASE_VIEW')")
    public List<PurchaseOrder> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId) {
        if (status != null) return service.getByStatus(status);
        if (supplierId != null) return service.getBySupplier(supplierId);
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PURCHASE_VIEW')")
    public PurchaseOrder getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public PurchaseOrder create(@Valid @RequestBody PurchaseOrder order) {
        return service.save(order);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public PurchaseOrder update(@PathVariable Long id, @Valid @RequestBody PurchaseOrder order) {
        return service.update(id, order);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public PurchaseOrder receiveDelivery(@PathVariable Long id, @Valid @RequestBody List<ReceiveItemDTO> items) {
        return service.receiveDelivery(id, items);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public PurchaseOrder cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
