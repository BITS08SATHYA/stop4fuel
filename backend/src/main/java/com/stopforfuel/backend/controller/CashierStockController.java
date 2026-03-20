package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.service.CashierStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cashier-stock")
@RequiredArgsConstructor
public class CashierStockController {

    private final CashierStockService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<CashierStock> getAll(@RequestParam(required = false) Long productId) {
        if (productId != null) {
            CashierStock stock = service.getByProduct(productId);
            return stock != null ? List.of(stock) : List.of();
        }
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public CashierStock getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public CashierStock create(@Valid @RequestBody CashierStock stock) {
        return service.save(stock);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public CashierStock update(@PathVariable Long id, @Valid @RequestBody CashierStock stock) {
        return service.update(id, stock);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
