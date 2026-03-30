package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Payment;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditManagementServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private InvoiceBillRepository invoiceBillRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StatementRepository statementRepository;

    @InjectMocks
    private CreditManagementService creditManagementService;

    private Customer testCustomer;
    private InvoiceBill unpaidBill;
    private Payment testPayment;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");
        testCustomer.setStatus("ACTIVE");
        testCustomer.setCreditLimitAmount(new BigDecimal("100000"));

        unpaidBill = new InvoiceBill();
        unpaidBill.setId(1L);
        unpaidBill.setCustomer(testCustomer);
        unpaidBill.setBillType("CREDIT");
        unpaidBill.setPaymentStatus("NOT_PAID");
        unpaidBill.setNetAmount(new BigDecimal("5000"));
        unpaidBill.setDate(LocalDateTime.now().minusDays(15));

        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setCustomer(testCustomer);
        testPayment.setAmount(new BigDecimal("2000"));
    }

    @Test
    void getCreditOverview_calculatesCorrectly() {
        when(customerRepository.findAll()).thenReturn(List.of(testCustomer));
        when(invoiceBillRepository.findByBillType("CREDIT")).thenReturn(List.of(unpaidBill));
        when(paymentRepository.findAll()).thenReturn(List.of(testPayment));
        when(statementRepository.findByCustomerIdAndStatus(1L, "NOT_PAID"))
                .thenReturn(Collections.emptyList());

        CreditManagementService.CreditOverview overview = creditManagementService.getCreditOverview(null);

        assertEquals(1, overview.getTotalCustomers());
        assertEquals(1, overview.getTotalCreditCustomers());
        assertEquals(new BigDecimal("5000"), overview.getTotalOutstanding());
        // Bill is 15 days old, so should be in 0-30 bucket
        assertEquals(new BigDecimal("5000"), overview.getTotalAging0to30());
        assertEquals(BigDecimal.ZERO, overview.getTotalAging31to60());
    }

    @Test
    void getCreditOverview_noCustomers_returnsEmpty() {
        when(customerRepository.findAll()).thenReturn(Collections.emptyList());
        when(invoiceBillRepository.findByBillType("CREDIT")).thenReturn(Collections.emptyList());
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());

        CreditManagementService.CreditOverview overview = creditManagementService.getCreditOverview(null);

        assertEquals(0, overview.getTotalCustomers());
        assertEquals(BigDecimal.ZERO, overview.getTotalOutstanding());
    }

    @Test
    void getCreditOverview_aging90Plus() {
        unpaidBill.setDate(LocalDateTime.now().minusDays(100));

        when(customerRepository.findAll()).thenReturn(List.of(testCustomer));
        when(invoiceBillRepository.findByBillType("CREDIT")).thenReturn(List.of(unpaidBill));
        when(paymentRepository.findAll()).thenReturn(Collections.emptyList());
        when(statementRepository.findByCustomerIdAndStatus(1L, "NOT_PAID"))
                .thenReturn(Collections.emptyList());

        CreditManagementService.CreditOverview overview = creditManagementService.getCreditOverview(null);

        assertEquals(new BigDecimal("5000"), overview.getTotalAging90Plus());
        assertEquals(BigDecimal.ZERO, overview.getTotalAging0to30());
    }

    @Test
    void getCreditOverview_ledgerBalance_isCorrect() {
        when(customerRepository.findAll()).thenReturn(List.of(testCustomer));
        when(invoiceBillRepository.findByBillType("CREDIT")).thenReturn(List.of(unpaidBill));
        when(paymentRepository.findAll()).thenReturn(List.of(testPayment));
        when(statementRepository.findByCustomerIdAndStatus(1L, "NOT_PAID"))
                .thenReturn(Collections.emptyList());

        CreditManagementService.CreditOverview overview = creditManagementService.getCreditOverview(null);

        CreditManagementService.CreditCustomerSummary summary = overview.getCustomers().get(0);
        // Billed 5000, paid 2000 = ledger balance 3000
        assertEquals(new BigDecimal("3000"), summary.getLedgerBalance());
        assertEquals(new BigDecimal("5000"), summary.getTotalBilled());
        assertEquals(new BigDecimal("2000"), summary.getTotalPaid());
    }

    @Test
    void getCustomerCreditDetail_returnsDetailWithSortedBills() {
        InvoiceBill paidBill = new InvoiceBill();
        paidBill.setId(2L);
        paidBill.setCustomer(testCustomer);
        paidBill.setBillType("CREDIT");
        paidBill.setPaymentStatus("PAID");
        paidBill.setDate(LocalDateTime.now().minusDays(30));

        when(invoiceBillRepository.findByBillType("CREDIT"))
                .thenReturn(List.of(unpaidBill, paidBill));
        when(statementRepository.findByCustomerId(1L)).thenReturn(new java.util.ArrayList<>());
        when(paymentRepository.findByCustomerId(1L)).thenReturn(new java.util.ArrayList<>(List.of(testPayment)));

        CreditManagementService.CreditCustomerDetail detail =
                creditManagementService.getCustomerCreditDetail(1L);

        assertEquals(1, detail.getUnpaidBills().size());
        assertEquals(1, detail.getPaidBills().size());
        assertEquals(1, detail.getPayments().size());
    }
}
