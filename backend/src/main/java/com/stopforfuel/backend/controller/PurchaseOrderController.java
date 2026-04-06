package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.PurchaseOrderDTO;
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
    public List<PurchaseOrderDTO> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId) {
        List<PurchaseOrder> result;
        if (status != null) result = service.getByStatus(status);
        else if (supplierId != null) result = service.getBySupplier(supplierId);
        else result = service.getAll();
        return result.stream().map(PurchaseOrderDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PURCHASE_VIEW')")
    public PurchaseOrderDTO getById(@PathVariable Long id) {
        return PurchaseOrderDTO.from(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PURCHASE_CREATE')")
    public PurchaseOrderDTO create(@Valid @RequestBody PurchaseOrder order) {
        return PurchaseOrderDTO.from(service.save(order));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PURCHASE_UPDATE')")
    public PurchaseOrderDTO update(@PathVariable Long id, @Valid @RequestBody PurchaseOrder order) {
        return PurchaseOrderDTO.from(service.update(id, order));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasPermission(null, 'PURCHASE_UPDATE')")
    public PurchaseOrderDTO receiveDelivery(@PathVariable Long id, @Valid @RequestBody List<ReceiveItemDTO> items) {
        return PurchaseOrderDTO.from(service.receiveDelivery(id, items));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasPermission(null, 'PURCHASE_UPDATE')")
    public PurchaseOrderDTO cancel(@PathVariable Long id) {
        return PurchaseOrderDTO.from(service.cancel(id));
    }
}
