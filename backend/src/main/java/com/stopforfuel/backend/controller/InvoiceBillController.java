package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.service.InvoiceBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class InvoiceBillController {

    private final InvoiceBillService service;

    @GetMapping
    public List<InvoiceBill> getAll() {
        return service.getAllInvoices();
    }

    @GetMapping("/shift/{shiftId}")
    public List<InvoiceBill> getByShift(@PathVariable Long shiftId) {
        return service.getInvoicesByShift(shiftId);
    }

    @GetMapping("/history")
    public Page<InvoiceBill> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String search) {
        return service.getInvoiceHistory(billType, paymentStatus, fromDate, toDate, search, PageRequest.of(page, size));
    }

    @GetMapping("/history/product-summary")
    public List<ProductSalesSummary> getProductSalesSummary(
            @RequestParam(required = false) String billType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        return service.getProductSalesSummary(billType, paymentStatus, fromDate, toDate);
    }

    @GetMapping("/{id}")
    public InvoiceBill getById(@PathVariable Long id) {
        return service.getInvoiceById(id);
    }

    @PostMapping
    public InvoiceBill create(@RequestBody InvoiceBill invoice) {
        return service.createInvoice(invoice);
    }

    @PutMapping("/{id}")
    public InvoiceBill update(@PathVariable Long id, @RequestBody InvoiceBill invoice) {
        return service.updateInvoice(id, invoice);
    }

    @GetMapping("/customer/{customerId}")
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
    public void delete(@PathVariable Long id) {
        service.deleteInvoice(id);
    }
}
