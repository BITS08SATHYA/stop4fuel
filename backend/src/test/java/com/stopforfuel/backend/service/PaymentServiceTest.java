package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private StatementRepository statementRepository;
    @Mock
    private InvoiceBillRepository invoiceBillRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PaymentModeRepository paymentModeRepository;
    @Mock
    private ShiftService shiftService;
    @Mock
    private EAdvanceService eAdvanceService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment testPayment;
    private Statement testStatement;
    private InvoiceBill testBill;
    private Customer testCustomer;
    private PaymentMode cashMode;

    @BeforeEach
    void setUp() {
        cashMode = new PaymentMode();
        cashMode.setId(1L);
        cashMode.setModeName("CASH");

        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");

        testStatement = new Statement();
        testStatement.setId(1L);
        testStatement.setStatementNo("S26/1001");
        testStatement.setCustomer(testCustomer);
        testStatement.setNetAmount(new BigDecimal("10000"));
        testStatement.setReceivedAmount(BigDecimal.ZERO);
        testStatement.setBalanceAmount(new BigDecimal("10000"));
        testStatement.setStatus("NOT_PAID");
        testStatement.setScid(1L);

        testBill = new InvoiceBill();
        testBill.setId(1L);
        testBill.setBillType("CREDIT");
        testBill.setPaymentStatus("NOT_PAID");
        testBill.setNetAmount(new BigDecimal("5000"));
        testBill.setCustomer(testCustomer);
        testBill.setScid(1L);

        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setAmount(new BigDecimal("3000"));
        testPayment.setPaymentMode(cashMode);
    }

    // --- Basic queries ---

    @Test
    void getPayments_returnsPaginatedResults() {
        Page<Payment> page = new PageImpl<>(List.of(testPayment));
        when(paymentRepository.findAllEager(any(PageRequest.class))).thenReturn(page);

        Page<Payment> result = paymentService.getPayments(null, null, null, null, PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getPaymentById_exists_returnsPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        Payment result = paymentService.getPaymentById(1L);

        assertEquals(new BigDecimal("3000"), result.getAmount());
    }

    @Test
    void getPaymentById_notFound_throwsException() {
        when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> paymentService.getPaymentById(99L));
    }

    // --- recordStatementPayment ---

    @Test
    void recordStatementPayment_validPayment_savesAndUpdatesStatement() {
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(paymentRepository.sumPaymentsByStatementId(1L)).thenReturn(new BigDecimal("3000"));
        when(paymentModeRepository.findById(1L)).thenReturn(Optional.of(cashMode));
        when(shiftService.getActiveShift()).thenReturn(null);

        Payment result = paymentService.recordStatementPayment(1L, testPayment);

        assertNotNull(result);
        assertEquals(testStatement, result.getStatement());
        assertEquals(testCustomer, result.getCustomer());
        verify(statementRepository).save(testStatement);
        assertEquals(new BigDecimal("3000"), testStatement.getReceivedAmount());
        assertEquals(new BigDecimal("7000"), testStatement.getBalanceAmount());
    }

    @Test
    void recordStatementPayment_fullyPaid_flipsStatusAndMarksBills() {
        testPayment.setAmount(new BigDecimal("10000"));
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(paymentRepository.sumPaymentsByStatementId(1L)).thenReturn(new BigDecimal("10000"));

        InvoiceBill bill1 = new InvoiceBill();
        bill1.setPaymentStatus("NOT_PAID");
        when(invoiceBillRepository.findByStatementId(1L)).thenReturn(List.of(bill1));
        when(paymentModeRepository.findById(1L)).thenReturn(Optional.of(cashMode));
        when(shiftService.getActiveShift()).thenReturn(null);

        paymentService.recordStatementPayment(1L, testPayment);

        assertEquals("PAID", testStatement.getStatus());
        assertEquals(BigDecimal.ZERO, testStatement.getBalanceAmount());
        assertEquals("PAID", bill1.getPaymentStatus());
    }

    @Test
    void recordStatementPayment_alreadyPaid_throwsException() {
        testStatement.setStatus("PAID");
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));

        assertThrows(RuntimeException.class,
                () -> paymentService.recordStatementPayment(1L, testPayment));
    }

    @Test
    void recordStatementPayment_zeroAmount_throwsException() {
        testPayment.setAmount(BigDecimal.ZERO);
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));

        assertThrows(RuntimeException.class,
                () -> paymentService.recordStatementPayment(1L, testPayment));
    }

    @Test
    void recordStatementPayment_exceedsBalance_throwsException() {
        testPayment.setAmount(new BigDecimal("15000"));
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));

        assertThrows(RuntimeException.class,
                () -> paymentService.recordStatementPayment(1L, testPayment));
    }

    // --- recordBillPayment ---

    @Test
    void recordBillPayment_validPayment_saves() {
        when(invoiceBillRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(paymentRepository.sumPaymentsByInvoiceBillId(1L)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            p.setId(10L);
            return p;
        });
        // After saving, check if fully paid
        when(paymentRepository.sumPaymentsByInvoiceBillId(1L))
                .thenReturn(BigDecimal.ZERO)      // first call: calculate balance
                .thenReturn(new BigDecimal("3000")); // second call: check if fully paid
        when(paymentModeRepository.findById(1L)).thenReturn(Optional.of(cashMode));
        when(shiftService.getActiveShift()).thenReturn(null);

        Payment result = paymentService.recordBillPayment(1L, testPayment);

        assertNotNull(result);
        assertEquals(testBill, result.getInvoiceBill());
        assertEquals(testCustomer, result.getCustomer());
    }

    @Test
    void recordBillPayment_nonCreditBill_throwsException() {
        testBill.setBillType("CASH");
        when(invoiceBillRepository.findById(1L)).thenReturn(Optional.of(testBill));

        assertThrows(RuntimeException.class,
                () -> paymentService.recordBillPayment(1L, testPayment));
    }

    @Test
    void recordBillPayment_alreadyPaid_throwsException() {
        testBill.setPaymentStatus("PAID");
        when(invoiceBillRepository.findById(1L)).thenReturn(Optional.of(testBill));

        assertThrows(RuntimeException.class,
                () -> paymentService.recordBillPayment(1L, testPayment));
    }

    @Test
    void recordBillPayment_exceedsBalance_throwsException() {
        testPayment.setAmount(new BigDecimal("6000"));
        when(invoiceBillRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(paymentRepository.sumPaymentsByInvoiceBillId(1L)).thenReturn(BigDecimal.ZERO);

        assertThrows(RuntimeException.class,
                () -> paymentService.recordBillPayment(1L, testPayment));
    }

    // --- Payment summaries ---

    @Test
    void getStatementPaymentSummary_returnsCorrectData() {
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        when(paymentRepository.findByStatementId(1L)).thenReturn(List.of(testPayment));

        PaymentService.StatementPaymentSummary summary = paymentService.getStatementPaymentSummary(1L);

        assertEquals(testStatement, summary.statement);
        assertEquals(1, summary.payments.size());
    }

    @Test
    void getBillPaymentSummary_returnsCorrectData() {
        when(invoiceBillRepository.findById(1L)).thenReturn(Optional.of(testBill));
        when(paymentRepository.findByInvoiceBillId(1L)).thenReturn(List.of(testPayment));
        when(paymentRepository.sumPaymentsByInvoiceBillId(1L)).thenReturn(new BigDecimal("3000"));

        PaymentService.BillPaymentSummary summary = paymentService.getBillPaymentSummary(1L);

        assertEquals(testBill, summary.invoiceBill);
        assertEquals(new BigDecimal("3000"), summary.totalReceived);
        assertEquals(new BigDecimal("2000"), summary.balanceAmount);
    }
}
