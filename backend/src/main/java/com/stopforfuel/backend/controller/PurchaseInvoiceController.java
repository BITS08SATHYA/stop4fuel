package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.PurchaseInvoiceDTO;
import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.service.PurchaseInvoiceService;
import com.stopforfuel.backend.service.PurchaseInvoiceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-invoices")
@RequiredArgsConstructor
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService service;
    private final PurchaseInvoiceReportService reportService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PURCHASE_VIEW')")
    public List<PurchaseInvoiceDTO> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<PurchaseInvoice> result;
        if (fromDate != null && toDate != null) result = service.getByDateRange(fromDate, toDate);
        else if (status != null) result = service.getByStatus(status);
        else if (supplierId != null) result = service.getBySupplier(supplierId);
        else if (type != null) result = service.getByType(type);
        else result = service.getAll();
        return result.stream().map(PurchaseInvoiceDTO::from).toList();
    }

    @GetMapping("/report")
    @PreAuthorize("hasPermission(null, 'PURCHASE_VIEW')")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "pdf") String format) {

        List<PurchaseInvoice> data = service.getByDateRange(fromDate, toDate);
        String dateRange = fromDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "_" + toDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

        if ("excel".equalsIgnoreCase(format)) {
            byte[] bytes = reportService.generateExcel(data, fromDate, toDate);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=PurchaseInvoices_" + dateRange + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } else {
            byte[] bytes = reportService.generatePdf(data, fromDate, toDate);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=PurchaseInvoices_" + dateRange + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PURCHASE_VIEW')")
    public PurchaseInvoiceDTO getById(@PathVariable Long id) {
        return PurchaseInvoiceDTO.from(service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public PurchaseInvoiceDTO create(@Valid @RequestBody PurchaseInvoice invoice) {
        return PurchaseInvoiceDTO.from(service.save(invoice));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public PurchaseInvoiceDTO update(@PathVariable Long id, @Valid @RequestBody PurchaseInvoice invoice) {
        return PurchaseInvoiceDTO.from(service.update(id, invoice));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public PurchaseInvoiceDTO updateStatus(@PathVariable Long id, @RequestParam String status) {
        return PurchaseInvoiceDTO.from(service.updateStatus(id, status));
    }

    @PostMapping("/{id}/upload-pdf")
    @PreAuthorize("hasPermission(null, 'PURCHASE_MANAGE')")
    public ResponseEntity<PurchaseInvoiceDTO> uploadPdf(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            PurchaseInvoice updated = service.uploadPdf(id, file);
            return ResponseEntity.ok(PurchaseInvoiceDTO.from(updated));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/pdf-url")
    @PreAuthorize("hasPermission(null, 'PURCHASE_VIEW')")
    public ResponseEntity<Map<String, String>> getPdfUrl(@PathVariable Long id) {
        try {
            String url = service.getPdfPresignedUrl(id);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
