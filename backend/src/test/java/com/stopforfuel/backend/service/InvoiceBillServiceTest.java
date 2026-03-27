package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceBillServiceTest {

    @Mock
    private InvoiceBillRepository repository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private TankInventoryRepository tankInventoryRepository;
    @Mock
    private NozzleInventoryRepository nozzleInventoryRepository;
    @Mock
    private ProductInventoryRepository productInventoryRepository;
    @Mock
    private NozzleRepository nozzleRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CashierStockRepository cashierStockRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private IncentiveService incentiveService;
    @Mock
    private ShiftService shiftService;
    @Mock
    private EAdvanceService eAdvanceService;
    @Mock
    private BillSequenceService billSequenceService;
    @Mock
    private S3StorageService s3StorageService;

    @InjectMocks
    private InvoiceBillService invoiceBillService;

    private InvoiceBill testInvoice;
    private Customer testCustomer;
    private Vehicle testVehicle;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");
        testCustomer.setStatus("ACTIVE");
        testCustomer.setConsumedLiters(BigDecimal.ZERO);

        testVehicle = new Vehicle();
        testVehicle.setId(1L);
        testVehicle.setVehicleNumber("TN01AB1234");
        testVehicle.setStatus("ACTIVE");
        testVehicle.setConsumedLiters(BigDecimal.ZERO);

        testProduct = new Product();
        testProduct.setId(1L);
        testProduct.setName("Diesel");
        testProduct.setPrice(new BigDecimal("89.50"));
        testProduct.setActive(true);

        testInvoice = new InvoiceBill();
        testInvoice.setBillType("CASH");
        testInvoice.setCustomer(testCustomer);
        testInvoice.setVehicle(testVehicle);

        when(billSequenceService.getNextBillNo(anyString())).thenReturn("C26/1");
    }

    @Test
    void getAllInvoices_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testInvoice));

        List<InvoiceBill> result = invoiceBillService.getAllInvoices();

        assertEquals(1, result.size());
    }

    @Test
    void getInvoicesByShift_returnsList() {
        when(repository.findByShiftId(1L)).thenReturn(List.of(testInvoice));

        List<InvoiceBill> result = invoiceBillService.getInvoicesByShift(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getInvoiceById_exists_returnsInvoice() {
        testInvoice.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));

        InvoiceBill result = invoiceBillService.getInvoiceById(1L);

        assertNotNull(result);
    }

    @Test
    void getInvoiceById_notFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> invoiceBillService.getInvoiceById(99L));
    }

    @Test
    void createInvoice_blockedCustomer_throwsException() {
        testCustomer.setStatus("BLOCKED");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        assertThrows(RuntimeException.class,
                () -> invoiceBillService.createInvoice(testInvoice));
    }

    @Test
    void createInvoice_inactiveCustomer_throwsException() {
        testCustomer.setStatus("INACTIVE");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));

        assertThrows(RuntimeException.class,
                () -> invoiceBillService.createInvoice(testInvoice));
    }

    @Test
    void createInvoice_blockedVehicle_throwsException() {
        testVehicle.setStatus("BLOCKED");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));

        assertThrows(RuntimeException.class,
                () -> invoiceBillService.createInvoice(testInvoice));
    }

    @Test
    void createInvoice_inactiveProduct_throwsException() {
        testProduct.setActive(false);
        InvoiceProduct ip = new InvoiceProduct();
        ip.setProduct(testProduct);
        ip.setQuantity(new BigDecimal("50"));
        ip.setUnitPrice(new BigDecimal("89.50"));
        testInvoice.setProducts(new ArrayList<>(List.of(ip)));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        assertThrows(RuntimeException.class,
                () -> invoiceBillService.createInvoice(testInvoice));
    }

    @Test
    void createInvoice_validCashInvoice_calculatesNetAmount() {
        InvoiceProduct ip = new InvoiceProduct();
        ip.setProduct(testProduct);
        ip.setQuantity(new BigDecimal("50"));
        ip.setUnitPrice(new BigDecimal("89.50"));
        testInvoice.setProducts(new ArrayList<>(List.of(ip)));

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(incentiveService.getActiveIncentive(1L, 1L)).thenReturn(Optional.empty());
        when(repository.save(any(InvoiceBill.class))).thenAnswer(i -> {
            InvoiceBill inv = i.getArgument(0);
            inv.setId(1L);
            return inv;
        });
        when(shiftService.getActiveShift()).thenReturn(null);

        InvoiceBill result = invoiceBillService.createInvoice(testInvoice);

        assertNotNull(result);
        // 50 * 89.50 = 4475.00
        assertEquals(new BigDecimal("4475.00"), result.getGrossAmount());
        assertEquals(new BigDecimal("4475.00"), result.getNetAmount());
        assertEquals(BigDecimal.ZERO, result.getTotalDiscount());
    }

    @Test
    void createInvoice_withIncentiveDiscount_appliesDiscount() {
        InvoiceProduct ip = new InvoiceProduct();
        ip.setProduct(testProduct);
        ip.setQuantity(new BigDecimal("100"));
        ip.setUnitPrice(new BigDecimal("89.50"));
        testInvoice.setProducts(new ArrayList<>(List.of(ip)));

        Incentive incentive = new Incentive();
        incentive.setMinQuantity(new BigDecimal("50"));
        incentive.setDiscountRate(new BigDecimal("2.00")); // Rs 2/liter discount

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(testVehicle));
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));
        when(incentiveService.getActiveIncentive(1L, 1L)).thenReturn(Optional.of(incentive));
        when(repository.save(any(InvoiceBill.class))).thenAnswer(i -> {
            InvoiceBill inv = i.getArgument(0);
            inv.setId(1L);
            return inv;
        });
        when(shiftService.getActiveShift()).thenReturn(null);

        InvoiceBill result = invoiceBillService.createInvoice(testInvoice);

        // Gross: 100 * 89.50 = 8950.00
        // Discount: 2.00 * 100 = 200.00
        // Net: 8950.00 - 200.00 = 8750.00
        assertEquals(new BigDecimal("8950.00"), result.getGrossAmount());
        assertEquals(new BigDecimal("200.00"), result.getTotalDiscount());
        assertEquals(new BigDecimal("8750.00"), result.getNetAmount());
    }

    @Test
    void createInvoice_noCustomer_savesWithoutCustomerCheck() {
        testInvoice.setCustomer(null);
        testInvoice.setVehicle(null);
        testInvoice.setProducts(null);

        when(repository.save(any(InvoiceBill.class))).thenAnswer(i -> {
            InvoiceBill inv = i.getArgument(0);
            inv.setId(1L);
            return inv;
        });

        InvoiceBill result = invoiceBillService.createInvoice(testInvoice);

        assertNotNull(result);
        verify(customerRepository, never()).findById(any());
    }

    @Test
    void deleteInvoice_callsDeleteById() {
        invoiceBillService.deleteInvoice(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void uploadFile_validBillPic_uploadsToS3AndSavesKey() throws Exception {
        testInvoice.setId(1L);
        testInvoice.setDate(LocalDateTime.of(2026, 3, 5, 10, 0));
        testInvoice.setBillPic(null);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(s3StorageService.upload(anyString(), any(MultipartFile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(InvoiceBill.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceBill result = invoiceBillService.uploadFile(1L, "bill-pic", file);

        assertNotNull(result.getBillPic());
        assertTrue(result.getBillPic().contains("bill-pic"));
        verify(s3StorageService).upload(anyString(), eq(file));
        verify(s3StorageService, never()).delete(anyString());
    }

    @Test
    void uploadFile_replacesExistingFile_deletesOldKey() throws Exception {
        testInvoice.setId(1L);
        testInvoice.setDate(LocalDateTime.of(2026, 3, 5, 10, 0));
        testInvoice.setBillPic("invoices/2026/03/05/1/bill-pic.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(s3StorageService.upload(anyString(), any(MultipartFile.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(InvoiceBill.class))).thenAnswer(i -> i.getArgument(0));

        invoiceBillService.uploadFile(1L, "bill-pic", file);

        verify(s3StorageService).delete("invoices/2026/03/05/1/bill-pic.jpg");
    }

    @Test
    void uploadFile_invalidType_throwsException() throws Exception {
        testInvoice.setId(1L);
        testInvoice.setDate(LocalDateTime.of(2026, 3, 5, 10, 0));
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(s3StorageService.upload(anyString(), any(MultipartFile.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(IllegalArgumentException.class,
                () -> invoiceBillService.uploadFile(1L, "invalid", file));
    }

    @Test
    void uploadFile_invoiceNotFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThrows(RuntimeException.class,
                () -> invoiceBillService.uploadFile(99L, "bill-pic", file));
    }

    @Test
    void getFilePresignedUrl_existingFile_returnsUrl() {
        testInvoice.setId(1L);
        testInvoice.setBillPic("invoices/2026/03/05/1/bill-pic.jpg");
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));
        when(s3StorageService.getPresignedUrl("invoices/2026/03/05/1/bill-pic.jpg"))
                .thenReturn("https://s3.example.com/test");

        String url = invoiceBillService.getFilePresignedUrl(1L, "bill-pic");

        assertEquals("https://s3.example.com/test", url);
        verify(s3StorageService).getPresignedUrl("invoices/2026/03/05/1/bill-pic.jpg");
    }

    @Test
    void getFilePresignedUrl_noFile_throwsException() {
        testInvoice.setId(1L);
        testInvoice.setBillPic(null);
        when(repository.findById(1L)).thenReturn(Optional.of(testInvoice));

        assertThrows(RuntimeException.class,
                () -> invoiceBillService.getFilePresignedUrl(1L, "bill-pic"));
    }

    @Test
    void uploadFile_emptyFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> invoiceBillService.uploadFile(1L, "bill-pic", file));
    }
}
