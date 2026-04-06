package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.ProductPriceHistoryDTO;
import com.stopforfuel.backend.entity.ProductPriceHistory;
import com.stopforfuel.backend.service.ProductPriceHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/product-price-history")
@RequiredArgsConstructor
public class ProductPriceHistoryController {

    private final ProductPriceHistoryService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<ProductPriceHistoryDTO> getAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (productId != null && from != null && to != null) {
            return service.getByProductAndDateRange(productId, from, to).stream()
                    .map(ProductPriceHistoryDTO::from).toList();
        } else if (productId != null) {
            return service.getByProduct(productId).stream()
                    .map(ProductPriceHistoryDTO::from).toList();
        } else if (from != null && to != null) {
            return service.getByDateRange(from, to).stream()
                    .map(ProductPriceHistoryDTO::from).toList();
        }
        return service.getByDateRange(
                LocalDate.now().minusYears(1), LocalDate.now()).stream()
                .map(ProductPriceHistoryDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_CREATE')")
    public ProductPriceHistoryDTO create(@Valid @RequestBody ProductPriceHistory entry) {
        return ProductPriceHistoryDTO.from(service.create(entry));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public ProductPriceHistoryDTO update(@PathVariable Long id, @Valid @RequestBody ProductPriceHistory entry) {
        return ProductPriceHistoryDTO.from(service.update(id, entry));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_DELETE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
