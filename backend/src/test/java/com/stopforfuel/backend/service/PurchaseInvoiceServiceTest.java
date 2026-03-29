package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.PurchaseInvoice;
import com.stopforfuel.backend.entity.PurchaseInvoiceItem;
import com.stopforfuel.backend.entity.Supplier;
import com.stopforfuel.backend.entity.PurchaseOrder;
import com.stopforfuel.backend.repository.PurchaseInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseInvoiceServiceTest {

    @Mock
    private PurchaseInvoiceRepository repository;
    @Mock
    private S3StorageService s3StorageService;

    @InjectMocks
    private PurchaseInvoiceService purchaseInvoiceService;

    private PurchaseInvoice testInvoice;
    private Supplier testSupplier;

    @BeforeEach
    void setUp() {
        testSupplier = new Supplier();
        testSupplier.setId(1L);
        testSupplier.setName("IOC");

        testInvoice = new PurchaseInvoice();
        testInvoice.setId(1L);
        testInvoice.setSupplier(testSupplier);
        testInvoice.setInvoiceNumber("INV-001");
        testInvoice.setInvoiceDate(LocalDate.now());
        testInvoice.setInvoiceType("FUEL");
        testInvoice.setStatus("PENDING");
        testInvoice.setTotalAmount(new BigDecimal("50000"));
        testInvoice.setScid(1L);
        testInvoice.setItems(new ArrayList<>());
    }

    @Test
    void getAll_returnsList() {
        when(repository.findByScidOrderByInvoiceDateDesc(1L)).thenReturn(List.of(testInvoice));

        List<PurchaseInvoice> result = purchaseInvoiceService.getAll();

        assertEquals(1, result.size());
        verify(repository).findByScidOrderByInvoiceDateDesc(1L);
    }

    @Test
    void getById_exists_returns() {
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));

        PurchaseInvoice result = purchaseInvoiceService.getById(1L);

        assertEquals("INV-001", result.getInvoiceNumber());
    }

    @Test
    void getById_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseInvoiceService.getById(99L));
        assertTrue(ex.getMessage().contains("PurchaseInvoice not found"));
    }

    @Test
    void getByStatus_returnsList() {
        when(repository.findByStatusAndScid("PENDING", 1L)).thenReturn(List.of(testInvoice));

        List<PurchaseInvoice> result = purchaseInvoiceService.getByStatus("PENDING");

        assertEquals(1, result.size());
        verify(repository).findByStatusAndScid("PENDING", 1L);
    }

    @Test
    void getBySupplier_returnsList() {
        when(repository.findBySupplierIdAndScid(1L, 1L)).thenReturn(List.of(testInvoice));

        List<PurchaseInvoice> result = purchaseInvoiceService.getBySupplier(1L);

        assertEquals(1, result.size());
        verify(repository).findBySupplierIdAndScid(1L, 1L);
    }

    @Test
    void getByType_returnsList() {
        when(repository.findByInvoiceTypeAndScid("FUEL", 1L)).thenReturn(List.of(testInvoice));

        List<PurchaseInvoice> result = purchaseInvoiceService.getByType("FUEL");

        assertEquals(1, result.size());
        verify(repository).findByInvoiceTypeAndScid("FUEL", 1L);
    }

    @Test
    void save_setsDefaultsAndSaves() {
        PurchaseInvoice newInvoice = new PurchaseInvoice();
        newInvoice.setSupplier(testSupplier);
        newInvoice.setInvoiceNumber("INV-002");
        newInvoice.setInvoiceType("FUEL");
        newInvoice.setTotalAmount(new BigDecimal("30000"));
        // scid, invoiceDate, status are all null

        when(repository.save(any(PurchaseInvoice.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseInvoice result = purchaseInvoiceService.save(newInvoice);

        assertEquals(1L, result.getScid());
        assertEquals("PENDING", result.getStatus());
        assertEquals(LocalDate.now(), result.getInvoiceDate());
        verify(repository).save(newInvoice);
    }

    @Test
    void update_pendingInvoice_updatesFields() {
        PurchaseInvoice details = new PurchaseInvoice();
        Supplier newSupplier = new Supplier();
        newSupplier.setId(2L);
        details.setSupplier(newSupplier);
        details.setPurchaseOrder(null);
        details.setInvoiceNumber("INV-UPDATED");
        details.setInvoiceDate(LocalDate.now().minusDays(1));
        details.setDeliveryDate(LocalDate.now());
        details.setInvoiceType("NON_FUEL");
        details.setTotalAmount(new BigDecimal("75000"));
        details.setRemarks("Updated invoice");
        details.setItems(new ArrayList<>());

        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(repository.save(any(PurchaseInvoice.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseInvoice result = purchaseInvoiceService.update(1L, details);

        assertEquals("INV-UPDATED", result.getInvoiceNumber());
        assertEquals("NON_FUEL", result.getInvoiceType());
        assertEquals(new BigDecimal("75000"), result.getTotalAmount());
        assertEquals("Updated invoice", result.getRemarks());
    }

    @Test
    void update_nonPendingInvoice_throws() {
        testInvoice.setStatus("VERIFIED");
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));

        PurchaseInvoice details = new PurchaseInvoice();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseInvoiceService.update(1L, details));
        assertTrue(ex.getMessage().contains("Can only edit purchase invoices in PENDING status"));
    }

    @Test
    void delete_pendingInvoice_deletes() {
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));

        purchaseInvoiceService.delete(1L);

        verify(repository).delete(testInvoice);
    }

    @Test
    void delete_nonPendingInvoice_throws() {
        testInvoice.setStatus("VERIFIED");
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> purchaseInvoiceService.delete(1L));
        assertTrue(ex.getMessage().contains("Can only delete purchase invoices in PENDING status"));
    }

    @Test
    void updateStatus_updatesStatus() {
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(repository.save(any(PurchaseInvoice.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseInvoice result = purchaseInvoiceService.updateStatus(1L, "VERIFIED");

        assertEquals("VERIFIED", result.getStatus());
        verify(repository).save(testInvoice);
    }

    @Test
    void uploadPdf_uploadsToS3AndSavesKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(s3StorageService.upload(anyString(), any(MultipartFile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(PurchaseInvoice.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseInvoice result = purchaseInvoiceService.uploadPdf(1L, file);

        assertNotNull(result.getPdfFilePath());
        assertTrue(result.getPdfFilePath().contains("invoices/purchase"));
        verify(s3StorageService).upload(anyString(), eq(file));
    }

    @Test
    void uploadPdf_replacesExistingFile_deletesOldKey() throws Exception {
        testInvoice.setPdfFilePath("purchase-invoices/2026/03/1/invoice.pdf");
        MockMultipartFile file = new MockMultipartFile("file", "invoice.pdf", "application/pdf", new byte[]{1, 2, 3});

        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(s3StorageService.upload(anyString(), any(MultipartFile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(PurchaseInvoice.class))).thenAnswer(i -> i.getArgument(0));

        purchaseInvoiceService.uploadPdf(1L, file);

        verify(s3StorageService).delete("purchase-invoices/2026/03/1/invoice.pdf");
    }

    @Test
    void getPdfPresignedUrl_exists_returnsUrl() {
        testInvoice.setPdfFilePath("purchase-invoices/2026/03/1/invoice.pdf");
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(s3StorageService.getPresignedUrl("purchase-invoices/2026/03/1/invoice.pdf"))
                .thenReturn("https://s3.example.com/test");

        String url = purchaseInvoiceService.getPdfPresignedUrl(1L);

        assertEquals("https://s3.example.com/test", url);
        verify(s3StorageService).getPresignedUrl("purchase-invoices/2026/03/1/invoice.pdf");
    }

    @Test
    void getPdfPresignedUrl_noPdf_throwsException() {
        testInvoice.setPdfFilePath(null);
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));

        assertThrows(RuntimeException.class,
                () -> purchaseInvoiceService.getPdfPresignedUrl(1L));
    }
}
