package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.InvoiceBillDTO;
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
    public List<InvoiceBillDTO> getAll() {
        return service.getAllInvoices().stream().map(InvoiceBillDTO::from).toList();
    }

    @GetMapping("/shift/{shiftId}")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public List<InvoiceBillDTO> getByShift(@PathVariable Long shiftId) {
        return service.getInvoicesByShift(shiftId).stream().map(InvoiceBillDTO::from).toList();
    }

    @GetMapping("/history")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public Page<InvoiceBillDTO> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String categoryType) {
        return service.getInvoiceHistory(billType, paymentStatus, categoryType, fromDate, toDate, search, PageRequest.of(page, size))
                .map(InvoiceBillDTO::from);
    }

    @GetMapping("/history/product-summary")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public List<ProductSalesSummary> getProductSalesSummary(
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String categoryType) {
        return service.getProductSalesSummary(billType, paymentStatus, categoryType, fromDate, toDate);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public InvoiceBillDTO getById(@PathVariable Long id) {
        return InvoiceBillDTO.from(service.getInvoiceById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public InvoiceBillDTO create(@Valid @RequestBody InvoiceBill invoice) {
        return InvoiceBillDTO.from(service.createInvoice(invoice));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public InvoiceBillDTO update(@PathVariable Long id, @Valid @RequestBody InvoiceBill invoice) {
        return InvoiceBillDTO.from(service.updateInvoice(id, invoice));
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public Page<InvoiceBillDTO> getByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        return service.getInvoicesByCustomer(customerId, billType, paymentStatus, fromDate, toDate, PageRequest.of(page, size))
                .map(InvoiceBillDTO::from);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public void delete(@PathVariable Long id) {
        service.deleteInvoice(id);
    }

    @PostMapping("/{id}/upload/{type}")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public ResponseEntity<InvoiceBillDTO> uploadFile(@PathVariable Long id,
                                                   @PathVariable String type,
                                                   @RequestParam("file") MultipartFile file) {
        try {
            InvoiceBill updated = service.uploadFile(id, type, file);
            return ResponseEntity.ok(InvoiceBillDTO.from(updated));
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
