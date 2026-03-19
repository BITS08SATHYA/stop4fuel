package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.entity.PurchaseInvoiceItem;
import com.stopforfuel.backend.repository.PurchaseInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository repository;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public List<PurchaseInvoice> getAll() {
        return repository.findByScidOrderByInvoiceDateDesc(1L);
    }

    public PurchaseInvoice getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("PurchaseInvoice not found with id: " + id));
    }

    public List<PurchaseInvoice> getByStatus(String status) {
        return repository.findByStatusAndScid(status, 1L);
    }

    public List<PurchaseInvoice> getBySupplier(Long supplierId) {
        return repository.findBySupplierIdAndScid(supplierId, 1L);
    }

    public List<PurchaseInvoice> getByType(String invoiceType) {
        return repository.findByInvoiceTypeAndScid(invoiceType, 1L);
    }

    public PurchaseInvoice save(PurchaseInvoice invoice) {
        if (invoice.getScid() == null) invoice.setScid(1L);
        if (invoice.getInvoiceDate() == null) invoice.setInvoiceDate(LocalDate.now());
        if (invoice.getStatus() == null) invoice.setStatus("PENDING");
        if (invoice.getItems() != null) {
            for (PurchaseInvoiceItem item : invoice.getItems()) {
                item.setPurchaseInvoice(invoice);
            }
        }
        return repository.save(invoice);
    }

    public PurchaseInvoice update(Long id, PurchaseInvoice details) {
        PurchaseInvoice existing = getById(id);
        if (!"PENDING".equals(existing.getStatus())) {
            throw new RuntimeException("Can only edit purchase invoices in PENDING status");
        }
        existing.setSupplier(details.getSupplier());
        existing.setPurchaseOrder(details.getPurchaseOrder());
        existing.setInvoiceNumber(details.getInvoiceNumber());
        existing.setInvoiceDate(details.getInvoiceDate());
        existing.setDeliveryDate(details.getDeliveryDate());
        existing.setInvoiceType(details.getInvoiceType());
        existing.setTotalAmount(details.getTotalAmount());
        existing.setRemarks(details.getRemarks());
        // Update items
        existing.getItems().clear();
        if (details.getItems() != null) {
            for (PurchaseInvoiceItem item : details.getItems()) {
                item.setPurchaseInvoice(existing);
                existing.getItems().add(item);
            }
        }
        return repository.save(existing);
    }

    public void delete(Long id) {
        PurchaseInvoice existing = getById(id);
        if (!"PENDING".equals(existing.getStatus())) {
            throw new RuntimeException("Can only delete purchase invoices in PENDING status");
        }
        repository.delete(existing);
    }

    public PurchaseInvoice updateStatus(Long id, String status) {
        PurchaseInvoice invoice = getById(id);
        invoice.setStatus(status);
        return repository.save(invoice);
    }

    public PurchaseInvoice uploadPdf(Long id, MultipartFile file) throws IOException {
        PurchaseInvoice invoice = getById(id);
        Path dir = Paths.get(uploadDir, "purchase-invoices", String.valueOf(id));
        Files.createDirectories(dir);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice.pdf";
        Path filePath = dir.resolve(filename);
        file.transferTo(filePath.toFile());
        invoice.setPdfFilePath(filePath.toString());
        return repository.save(invoice);
    }

    public Path getPdfPath(Long id) {
        PurchaseInvoice invoice = getById(id);
        if (invoice.getPdfFilePath() == null) {
            throw new RuntimeException("No PDF uploaded for this invoice");
        }
        return Paths.get(invoice.getPdfFilePath());
    }
}
