package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.CashierStockDTO;
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
    public List<CashierStockDTO> getAll(@RequestParam(required = false) Long productId) {
        if (productId != null) {
            CashierStock stock = service.getByProduct(productId);
            return stock != null ? List.of(CashierStockDTO.from(stock)) : List.of();
        }
        return service.getAll().stream().map(CashierStockDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public CashierStockDTO getById(@PathVariable Long id) {
        return CashierStockDTO.from(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public CashierStockDTO create(@Valid @RequestBody CashierStock stock) {
        return CashierStockDTO.from(service.save(stock));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public CashierStockDTO update(@PathVariable Long id, @Valid @RequestBody CashierStock stock) {
        return CashierStockDTO.from(service.update(id, stock));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
