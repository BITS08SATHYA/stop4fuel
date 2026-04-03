package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ProductInventoryDTO;
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
    public List<ProductInventoryDTO> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<ProductInventory> result;
        if (fromDate != null && toDate != null) {
            if (productId != null) result = service.getByProductAndDateRange(productId, fromDate, toDate);
            else result = service.getByDateRange(fromDate, toDate);
        } else if (date != null) result = service.getByDate(date);
        else if (productId != null) result = service.getByProductId(productId);
        else result = service.getAll();
        return result.stream().map(ProductInventoryDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ProductInventoryDTO getById(@PathVariable Long id) {
        return ProductInventoryDTO.from(service.getById(id));
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
    public ProductInventoryDTO create(@Valid @RequestBody ProductInventory inventory) {
        return ProductInventoryDTO.from(service.save(inventory));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ProductInventoryDTO update(@PathVariable Long id, @Valid @RequestBody ProductInventory inventory) {
        return ProductInventoryDTO.from(service.update(id, inventory));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
