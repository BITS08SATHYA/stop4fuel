package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.InvoiceBillDTO;
import com.stopforfuel.backend.dto.OutstandingBillDTO;
import com.stopforfuel.backend.dto.ProductSalesSummary;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.enums.BillType;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceBillController {

    private final InvoiceBillService service;
    private final PaymentRepository paymentRepository;
    private final com.stopforfuel.backend.service.BillSequenceService billSequenceService;

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
        return service.getInvoiceHistory(billType, paymentStatus, categoryType, fromDate, toDate, search, PageRequest.of(page, Math.min(size, 100)))
                .map(InvoiceBillDTO::from);
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasPermission(null, 'PAYMENT_VIEW')")
    public Page<OutstandingBillDTO> getOutstanding(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BigDecimal maxBalance) {
        return service.findOutstanding(fromDate, toDate, search, maxBalance,
                        PageRequest.of(page, Math.min(size, 100)))
                .map(b -> OutstandingBillDTO.from(b, paymentRepository.sumPaymentsByInvoiceBillId(b.getId())));
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

    @PutMapping("/{id}/move")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'SYSTEM_ADMIN')")
    public InvoiceBillDTO move(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object targetRaw = body.get("targetShiftId");
        Object dateRaw = body.get("newBillDate");
        if (targetRaw == null) {
            throw new IllegalArgumentException("targetShiftId is required");
        }
        if (dateRaw == null) {
            throw new IllegalArgumentException("newBillDate is required");
        }
        Long targetShiftId = Long.parseLong(targetRaw.toString());
        LocalDateTime newBillDate = LocalDateTime.parse(dateRaw.toString());
        return InvoiceBillDTO.from(service.moveInvoice(id, targetShiftId, newBillDate));
    }

    /**
     * Admin-only: set an invoice's shift_id without changing its bill_date. Used by the
     * orphan-bill recovery UI to attach a NULL-shift bill to its covering shift. Different
     * from /move, which also rewrites bill_date.
     */
    @PatchMapping("/{id}/shift")
    @PreAuthorize("hasPermission(null, 'PAYMENT_UPDATE')")
    public InvoiceBillDTO setShift(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("shiftId");
        if (raw == null) {
            throw new IllegalArgumentException("shiftId is required");
        }
        Long shiftId = Long.parseLong(raw.toString());
        return InvoiceBillDTO.from(service.setShift(id, shiftId));
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
        return service.getInvoicesByCustomer(customerId, billType, paymentStatus, fromDate, toDate, PageRequest.of(page, Math.min(size, 100)))
                .map(InvoiceBillDTO::from);
    }

    @PatchMapping("/{id}/independent")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public InvoiceBillDTO markIndependent(@PathVariable Long id) {
        return InvoiceBillDTO.from(service.markIndependent(id));
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

    @GetMapping("/{id}/photos")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public List<InvoiceBillDTO.PhotoDTO> getPhotos(@PathVariable Long id,
                                                    @RequestParam(required = false) String type) {
        var photos = type != null ? service.getPhotosByType(id, type) : service.getPhotos(id);
        return photos.stream().map(InvoiceBillDTO.PhotoDTO::from).toList();
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasPermission(null, 'INVOICE_CREATE')")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        service.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Peek the next bill number for CASH or CREDIT without consuming the sequence.
     * Read-only; surfaces highestInDb so the UI can flag drift.
     */
    @GetMapping("/sequence/peek")
    @PreAuthorize("hasPermission(null, 'INVOICE_VIEW')")
    public com.stopforfuel.backend.service.BillSequenceService.NextBillNoView peekSequence(
            @RequestParam BillType billType) {
        requireCashOrCredit(billType);
        return billSequenceService.peekNextBillNo(billType);
    }

    /**
     * Set the next CASH/CREDIT bill number (admin sequence reset / fast-forward).
     * Forward-only by design — existing bills are not renumbered. Rejects values that
     * would re-issue a number already on a bill (would create a duplicate bill_no).
     */
    @PutMapping("/sequence/next")
    @PreAuthorize("hasPermission(null, 'INVOICE_UPDATE')")
    public com.stopforfuel.backend.service.BillSequenceService.NextBillNoView setSequence(
            @RequestBody Map<String, Object> body) {
        BillType billType = BillType.valueOf(String.valueOf(body.get("billType")));
        requireCashOrCredit(billType);
        Object raw = body.get("nextNumber");
        if (raw == null) {
            throw new IllegalArgumentException("nextNumber is required");
        }
        long next = Long.parseLong(String.valueOf(raw));
        Long highestInDb = billSequenceService.peekNextBillNo(billType).highestInDb();
        if (highestInDb != null && next <= highestInDb) {
            throw new IllegalArgumentException(
                    "Next number " + next + " would duplicate an existing bill (highest is "
                            + highestInDb + "). Choose " + (highestInDb + 1) + " or higher.");
        }
        return billSequenceService.setNextBillNo(billType, next);
    }

    private void requireCashOrCredit(BillType billType) {
        if (billType != BillType.CASH && billType != BillType.CREDIT) {
            throw new IllegalArgumentException("billType must be CASH or CREDIT");
        }
    }
}
