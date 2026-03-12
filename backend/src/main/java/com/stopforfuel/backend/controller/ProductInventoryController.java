package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.service.ProductInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/products")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProductInventoryController {

    private final ProductInventoryService service;

    @GetMapping
    public List<ProductInventory> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long productId) {
        if (date != null) return service.getByDate(date);
        if (productId != null) return service.getByProductId(productId);
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ProductInventory getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ProductInventory create(@RequestBody ProductInventory inventory) {
        return service.save(inventory);
    }

    @PutMapping("/{id}")
    public ProductInventory update(@PathVariable Long id, @RequestBody ProductInventory inventory) {
        return service.update(id, inventory);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
