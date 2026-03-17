package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.ReceiveItemDTO;
import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @GetMapping
    public List<PurchaseOrder> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId) {
        if (status != null) return service.getByStatus(status);
        if (supplierId != null) return service.getBySupplier(supplierId);
        return service.getAll();
    }

    @GetMapping("/{id}")
    public PurchaseOrder getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public PurchaseOrder create(@RequestBody PurchaseOrder order) {
        return service.save(order);
    }

    @PutMapping("/{id}")
    public PurchaseOrder update(@PathVariable Long id, @RequestBody PurchaseOrder order) {
        return service.update(id, order);
    }

    @PostMapping("/{id}/receive")
    public PurchaseOrder receiveDelivery(@PathVariable Long id, @RequestBody List<ReceiveItemDTO> items) {
        return service.receiveDelivery(id, items);
    }

    @PatchMapping("/{id}/cancel")
    public PurchaseOrder cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
