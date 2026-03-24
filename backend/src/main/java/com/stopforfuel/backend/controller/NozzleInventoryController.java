package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.service.NozzleInventoryService;
import com.stopforfuel.backend.service.NozzleInventoryReportService;
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
@RequestMapping("/api/inventory/nozzles")
@RequiredArgsConstructor
public class NozzleInventoryController {

    private final NozzleInventoryService service;
    private final NozzleInventoryReportService reportService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<NozzleInventory> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long nozzleId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            if (nozzleId != null) return service.getByNozzleAndDateRange(nozzleId, fromDate, toDate);
            if (productId != null) return service.getByProductAndDateRange(productId, fromDate, toDate);
            return service.getByDateRange(fromDate, toDate);
        }
        if (date != null) return service.getByDate(date);
        if (nozzleId != null) return service.getByNozzleId(nozzleId);
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public NozzleInventory getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/report")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long nozzleId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(defaultValue = "detailed") String reportType) {

        List<NozzleInventory> data;
        String filterLabel;
        // Determine reportType: product_sales or meter_tracker when product is selected, detailed otherwise
        String effectiveType = reportType;
        if (nozzleId != null) {
            data = service.getByNozzleAndDateRange(nozzleId, fromDate, toDate);
            filterLabel = !data.isEmpty() ? data.get(0).getNozzle().getNozzleName() : "Nozzle";
            effectiveType = "detailed";
        } else if (productId != null) {
            data = service.getByProductAndDateRange(productId, fromDate, toDate);
            filterLabel = !data.isEmpty() ? data.get(0).getNozzle().getTank().getProduct().getName() : "Product";
            // Default to product_sales if not explicitly set to meter_tracker
            if ("detailed".equals(effectiveType)) effectiveType = "product_sales";
        } else {
            data = service.getByDateRange(fromDate, toDate);
            filterLabel = "All_Nozzles";
            effectiveType = "detailed";
        }

        String dateRange = fromDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "_" + toDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
        String filePrefix = switch (effectiveType) {
            case "product_sales" -> "ProductDailySales";
            case "meter_tracker" -> "MeterReadingTracker";
            default -> "NozzleInventory";
        };

        if ("excel".equalsIgnoreCase(format)) {
            byte[] bytes = reportService.generateExcel(data, fromDate, toDate, filterLabel, effectiveType);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filePrefix + "_" + filterLabel + "_" + dateRange + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } else {
            byte[] bytes = reportService.generatePdf(data, fromDate, toDate, filterLabel, effectiveType);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filePrefix + "_" + filterLabel + "_" + dateRange + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        }
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public NozzleInventory create(@Valid @RequestBody NozzleInventory inventory) {
        return service.save(inventory);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public NozzleInventory update(@PathVariable Long id, @Valid @RequestBody NozzleInventory inventory) {
        return service.update(id, inventory);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
