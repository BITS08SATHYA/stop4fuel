package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.ProductInventory;
import com.stopforfuel.backend.service.ProductInventoryService;
import com.stopforfuel.backend.service.ProductInventoryReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/products")
@RequiredArgsConstructor
public class ProductInventoryController {

    private final ProductInventoryService service;
    private final ProductInventoryReportService reportService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<ProductInventory> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            if (productId != null) return service.getByProductAndDateRange(productId, fromDate, toDate);
            return service.getByDateRange(fromDate, toDate);
        }
        if (date != null) return service.getByDate(date);
        if (productId != null) return service.getByProductId(productId);
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ProductInventory getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/report")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "pdf") String format) {

        List<ProductInventory> data = productId != null
                ? service.getByProductAndDateRange(productId, fromDate, toDate)
                : service.getByDateRange(fromDate, toDate);

        String productName = (productId != null && !data.isEmpty()) ? data.get(0).getProduct().getName() : "All_Products";
        String dateRange = fromDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "_" + toDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

        if ("excel".equalsIgnoreCase(format)) {
            byte[] bytes = reportService.generateExcel(data, fromDate, toDate, productName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ProductInventory_" + productName + "_" + dateRange + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } else {
            byte[] bytes = reportService.generatePdf(data, fromDate, toDate, productName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ProductInventory_" + productName + "_" + dateRange + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        }
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ProductInventory create(@Valid @RequestBody ProductInventory inventory) {
        return service.save(inventory);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ProductInventory update(@PathVariable Long id, @Valid @RequestBody ProductInventory inventory) {
        return service.update(id, inventory);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
