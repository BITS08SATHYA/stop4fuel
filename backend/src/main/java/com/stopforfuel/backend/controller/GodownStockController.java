package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.GodownStockDTO;
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
    public List<GodownStockDTO> getAll(@RequestParam(required = false) Long productId) {
        if (productId != null) {
            GodownStock stock = service.getByProduct(productId);
            return stock != null ? List.of(GodownStockDTO.from(stock)) : List.of();
        }
        return service.getAll().stream().map(GodownStockDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public GodownStockDTO getById(@PathVariable Long id) {
        return GodownStockDTO.from(service.getById(id));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<GodownStockDTO> getLowStockItems() {
        return service.getLowStockItems().stream().map(GodownStockDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public GodownStockDTO create(@Valid @RequestBody GodownStock stock) {
        return GodownStockDTO.from(service.save(stock));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public GodownStockDTO update(@PathVariable Long id, @Valid @RequestBody GodownStock stock) {
        return GodownStockDTO.from(service.update(id, stock));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
