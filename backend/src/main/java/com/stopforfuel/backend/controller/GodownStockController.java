package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.GodownStock;
import com.stopforfuel.backend.service.GodownStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/godown")
@RequiredArgsConstructor
public class GodownStockController {

    private final GodownStockService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<GodownStock> getAll(@RequestParam(required = false) Long productId) {
        if (productId != null) {
            GodownStock stock = service.getByProduct(productId);
            return stock != null ? List.of(stock) : List.of();
        }
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public GodownStock getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<GodownStock> getLowStockItems() {
        return service.getLowStockItems();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public GodownStock create(@Valid @RequestBody GodownStock stock) {
        return service.save(stock);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public GodownStock update(@PathVariable Long id, @Valid @RequestBody GodownStock stock) {
        return service.update(id, stock);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
