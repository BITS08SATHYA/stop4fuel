package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.service.PurchaseInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-invoices")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceService service;

    @GetMapping
    public List<PurchaseInvoice> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String type) {
        if (status != null) return service.getByStatus(status);
        if (supplierId != null) return service.getBySupplier(supplierId);
        if (type != null) return service.getByType(type);
        return service.getAll();
    }

    @GetMapping("/{id}")
    public PurchaseInvoice getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public PurchaseInvoice create(@Valid @RequestBody PurchaseInvoice invoice) {
        return service.save(invoice);
    }

    @PutMapping("/{id}")
    public PurchaseInvoice update(@PathVariable Long id, @Valid @RequestBody PurchaseInvoice invoice) {
        return service.update(id, invoice);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public PurchaseInvoice updateStatus(@PathVariable Long id, @RequestParam String status) {
        return service.updateStatus(id, status);
    }

    @PostMapping("/{id}/upload-pdf")
    public ResponseEntity<PurchaseInvoice> uploadPdf(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            PurchaseInvoice updated = service.uploadPdf(id, file);
            return ResponseEntity.ok(updated);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/pdf-url")
    public ResponseEntity<Map<String, String>> getPdfUrl(@PathVariable Long id) {
        try {
            String url = service.getPdfPresignedUrl(id);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
