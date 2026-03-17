package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.CashierStock;
import com.stopforfuel.backend.service.CashierStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cashier-stock")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CashierStockController {

    private final CashierStockService service;

    @GetMapping
    public List<CashierStock> getAll(@RequestParam(required = false) Long productId) {
        if (productId != null) {
            CashierStock stock = service.getByProduct(productId);
            return stock != null ? List.of(stock) : List.of();
        }
        return service.getAll();
    }

    @GetMapping("/{id}")
    public CashierStock getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public CashierStock create(@RequestBody CashierStock stock) {
        return service.save(stock);
    }

    @PutMapping("/{id}")
    public CashierStock update(@PathVariable Long id, @RequestBody CashierStock stock) {
        return service.update(id, stock);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
