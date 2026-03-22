package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.service.InvoiceBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceBillController {

    private final InvoiceBillService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public List<InvoiceBill> getAll() {
        return service.getAllInvoices();
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public List<InvoiceBill> getByShift(@PathVariable Long shiftId) {
        return service.getInvoicesByShift(shiftId);
    }

    @GetMapping("/history")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public Page<InvoiceBill> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String customerCategory) {
        return service.getInvoiceHistory(billType, paymentStatus, customerCategory, fromDate, toDate, search, PageRequest.of(page, size));
    }

    @GetMapping("/history/product-summary")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public List<ProductSalesSummary> getProductSalesSummary(
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String customerCategory) {
        return service.getProductSalesSummary(billType, paymentStatus, customerCategory, fromDate, toDate);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public InvoiceBill getById(@PathVariable Long id) {
        return service.getInvoiceById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public InvoiceBill create(@Valid @RequestBody InvoiceBill invoice) {
        return service.createInvoice(invoice);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public InvoiceBill update(@PathVariable Long id, @Valid @RequestBody InvoiceBill invoice) {
        return service.updateInvoice(id, invoice);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public Page<InvoiceBill> getByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        return service.getInvoicesByCustomer(customerId, billType, paymentStatus, fromDate, toDate, PageRequest.of(page, size));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public void delete(@PathVariable Long id) {
        service.deleteInvoice(id);
    }

    @PostMapping("/{id}/upload/{type}")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public ResponseEntity<InvoiceBill> uploadFile(@PathVariable Long id,
                                                   @PathVariable String type,
                                                   @RequestParam("file") MultipartFile file) {
        try {
            InvoiceBill updated = service.uploadFile(id, type, file);
            return ResponseEntity.ok(updated);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/file-url")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public ResponseEntity<Map<String, String>> getFileUrl(@PathVariable Long id,
                                                           @RequestParam String type) {
        String url = service.getFilePresignedUrl(id, type);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
