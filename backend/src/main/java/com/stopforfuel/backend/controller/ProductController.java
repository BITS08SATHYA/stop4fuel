package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ProductDTO;
import com.stopforfuel.backend.dto.ProductSalesHistoryDTO;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<ProductDTO> getAllProducts() {
        return productService.getAllProducts().stream().map(ProductDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<ProductDTO> getActiveProducts() {
        return productService.getActiveProducts().stream().map(ProductDTO::from).toList();
    }

    @GetMapping("/active/non-fuel")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<ProductDTO> getActiveNonFuelProducts() {
        return productService.getActiveNonFuelProducts().stream().map(ProductDTO::from).toList();
    }

    @GetMapping("/top-selling")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<ProductDTO> getTopSellingProducts(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "8") int limit) {
        return productService.getTopSellingProducts(days, limit).stream().map(ProductDTO::from).toList();
    }

    @GetMapping("/category/{category}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<ProductDTO> getProductsByCategory(@PathVariable String category) {
        return productService.getProductsByCategory(category).stream().map(ProductDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public ProductDTO getProductById(@PathVariable Long id) {
        return ProductDTO.from(productService.getProductById(id));
    }

    @GetMapping("/{id}/sales-history")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public ProductSalesHistoryDTO getSalesHistory(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "DAY") String granularity) {
        return productService.getSalesHistory(id, fromDate, toDate, granularity);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_CREATE')")
    public ProductDTO createProduct(@Valid @RequestBody Product product) {
        return ProductDTO.from(productService.createProduct(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public ProductDTO updateProduct(@PathVariable Long id, @Valid @RequestBody Product product) {
        return ProductDTO.from(productService.updateProduct(id, product));
    }

    @PatchMapping("/{id}/price")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public ProductDTO updatePrice(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        BigDecimal price = body.get("price");
        if (price == null) {
            throw new IllegalArgumentException("Price is required");
        }
        return ProductDTO.from(productService.updatePrice(id, price));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public ProductDTO toggleStatus(@PathVariable Long id) {
        return ProductDTO.from(productService.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_DELETE')")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok().build();
    }
}

