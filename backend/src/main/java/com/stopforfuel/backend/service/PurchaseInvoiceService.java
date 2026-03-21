package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.entity.PurchaseInvoiceItem;
import com.stopforfuel.backend.repository.PurchaseInvoiceRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository repository;
    private final S3StorageService s3StorageService;

    public List<PurchaseInvoice> getAll() {
        return repository.findByScidOrderByInvoiceDateDesc(SecurityUtils.getScid());
    }

    public PurchaseInvoice getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("PurchaseInvoice not found with id: " + id));
    }

    public List<PurchaseInvoice> getByStatus(String status) {
        return repository.findByStatusAndScid(status, SecurityUtils.getScid());
    }

    public List<PurchaseInvoice> getBySupplier(Long supplierId) {
        return repository.findBySupplierIdAndScid(supplierId, SecurityUtils.getScid());
    }

    public List<PurchaseInvoice> getByType(String invoiceType) {
        return repository.findByInvoiceTypeAndScid(invoiceType, SecurityUtils.getScid());
    }

    public PurchaseInvoice save(PurchaseInvoice invoice) {
        if (invoice.getScid() == null) invoice.setScid(SecurityUtils.getScid());
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
        LocalDate date = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now();
        String ext = getExtension(file.getOriginalFilename());
        String key = String.format("purchase-invoices/%d/%02d/%d/invoice.%s",
                date.getYear(), date.getMonthValue(), id, ext);

        // Delete old file if exists
        if (invoice.getPdfFilePath() != null && !invoice.getPdfFilePath().isEmpty()) {
            try { s3StorageService.delete(invoice.getPdfFilePath()); } catch (Exception ignored) {}
        }

        s3StorageService.upload(key, file);
        invoice.setPdfFilePath(key);
        return repository.save(invoice);
    }

    public String getPdfPresignedUrl(Long id) {
        PurchaseInvoice invoice = getById(id);
        if (invoice.getPdfFilePath() == null || invoice.getPdfFilePath().isEmpty()) {
            throw new RuntimeException("No PDF uploaded for this invoice");
        }
        return s3StorageService.getPresignedUrl(invoice.getPdfFilePath());
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "pdf";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
