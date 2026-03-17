package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.StatementRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private InvoiceBillRepository invoiceBillRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BillSequenceService billSequenceService;

    @InjectMocks
    private StatementService statementService;

    private Statement testStatement;
    private Customer testCustomer;
    private InvoiceBill testBill;

    @BeforeEach
    void setUp() {
        testCustomer = new Customer();
        testCustomer.setId(1L);
        testCustomer.setName("Test Customer");
        testCustomer.setScid(1L);

        testBill = new InvoiceBill();
        testBill.setId(10L);
        testBill.setCustomer(testCustomer);
        testBill.setBillType("CREDIT");
        testBill.setNetAmount(new BigDecimal("5000.75"));
        testBill.setDate(LocalDateTime.of(2026, 3, 5, 10, 0));

        testStatement = new Statement();
        testStatement.setId(1L);
        testStatement.setStatementNo("S26/100");
        testStatement.setCustomer(testCustomer);
        testStatement.setStatus("NOT_PAID");
        testStatement.setReceivedAmount(BigDecimal.ZERO);
    }

    @Test
    void getStatements_returnsPage() {
        Page<Statement> page = new PageImpl<>(List.of(testStatement));
        when(statementRepository.findWithFilters(eq(1L), eq("NOT_PAID"), any()))
                .thenReturn(page);

        assertEquals(1, statementService.getStatements(1L, "NOT_PAID", PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void getAllStatements_returnsList() {
        when(statementRepository.findAll()).thenReturn(List.of(testStatement));
        assertEquals(1, statementService.getAllStatements().size());
    }

    @Test
    void getStatementById_exists_returnsStatement() {
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        assertEquals("S26/100", statementService.getStatementById(1L).getStatementNo());
    }

    @Test
    void getStatementById_notExists_throwsException() {
        when(statementRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> statementService.getStatementById(99L));
    }

    @Test
    void getStatementByNo_exists_returnsStatement() {
        when(statementRepository.findByStatementNo("S26/100")).thenReturn(Optional.of(testStatement));
        assertNotNull(statementService.getStatementByNo("S26/100"));
    }

    @Test
    void getStatementsByCustomer_returnsList() {
        when(statementRepository.findByCustomerId(1L)).thenReturn(List.of(testStatement));
        assertEquals(1, statementService.getStatementsByCustomer(1L).size());
    }

    @Test
    void getOutstandingStatements_returnsList() {
        when(statementRepository.findByStatus("NOT_PAID")).thenReturn(List.of(testStatement));
        assertEquals(1, statementService.getOutstandingStatements().size());
    }

    @Test
    void generateStatement_withDateRange_createsStatement() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(invoiceBillRepository.findUnlinkedCreditBills(eq(1L), any(), any()))
                .thenReturn(List.of(testBill));
        when(billSequenceService.getNextBillNo("STMT")).thenReturn("S26/1");
        when(statementRepository.save(any(Statement.class))).thenAnswer(i -> {
            Statement s = i.getArgument(0);
            s.setId(2L);
            return s;
        });

        Statement result = statementService.generateStatement(
                1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                null, null, null);

        assertEquals("S26/1", result.getStatementNo());
        assertEquals(1, result.getNumberOfBills());
        assertEquals("NOT_PAID", result.getStatus());
        verify(invoiceBillRepository).save(testBill);
    }

    @Test
    void generateStatement_withBillIds_createsStatement() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(invoiceBillRepository.findUnlinkedCreditBillsByIds(List.of(10L)))
                .thenReturn(List.of(testBill));
        when(billSequenceService.getNextBillNo("STMT")).thenReturn("S26/1");
        when(statementRepository.save(any(Statement.class))).thenAnswer(i -> {
            Statement s = i.getArgument(0);
            s.setId(1L);
            return s;
        });

        Statement result = statementService.generateStatement(
                1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                null, null, List.of(10L));

        assertEquals("S26/1", result.getStatementNo());
    }

    @Test
    void generateStatement_noBills_throwsException() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(invoiceBillRepository.findUnlinkedCreditBills(eq(1L), any(), any()))
                .thenReturn(List.of());

        assertThrows(RuntimeException.class,
                () -> statementService.generateStatement(
                        1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        null, null, null));
    }

    @Test
    void generateStatement_billNotBelongingToCustomer_throwsException() {
        Customer otherCustomer = new Customer();
        otherCustomer.setId(2L);
        testBill.setCustomer(otherCustomer);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(testCustomer));
        when(invoiceBillRepository.findUnlinkedCreditBillsByIds(List.of(10L)))
                .thenReturn(List.of(testBill));

        assertThrows(RuntimeException.class,
                () -> statementService.generateStatement(
                        1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                        null, null, List.of(10L)));
    }

    @Test
    void removeBillFromStatement_recalculatesTotals() {
        testStatement.setReceivedAmount(BigDecimal.ZERO);
        testBill.setStatement(testStatement);

        InvoiceBill remainingBill = new InvoiceBill();
        remainingBill.setId(11L);
        remainingBill.setNetAmount(new BigDecimal("3000"));

        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        when(invoiceBillRepository.findById(10L)).thenReturn(Optional.of(testBill));
        when(invoiceBillRepository.findByStatementId(1L)).thenReturn(List.of(remainingBill));
        when(statementRepository.save(any(Statement.class))).thenAnswer(i -> i.getArgument(0));

        Statement result = statementService.removeBillFromStatement(1L, 10L);

        assertNull(testBill.getStatement());
        assertEquals(1, result.getNumberOfBills());
        assertEquals(new BigDecimal("3000"), result.getNetAmount());
    }

    @Test
    void removeBillFromStatement_billNotInStatement_throwsException() {
        testBill.setStatement(null);

        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        when(invoiceBillRepository.findById(10L)).thenReturn(Optional.of(testBill));

        assertThrows(RuntimeException.class,
                () -> statementService.removeBillFromStatement(1L, 10L));
    }

    @Test
    void removeBillFromStatement_fullyPaidAfterRemoval_marksPaid() {
        testStatement.setReceivedAmount(new BigDecimal("5000"));

        testBill.setStatement(testStatement);

        InvoiceBill remainingBill = new InvoiceBill();
        remainingBill.setId(11L);
        remainingBill.setNetAmount(new BigDecimal("3000"));

        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        when(invoiceBillRepository.findById(10L)).thenReturn(Optional.of(testBill));
        when(invoiceBillRepository.findByStatementId(1L)).thenReturn(List.of(remainingBill));
        when(statementRepository.save(any(Statement.class))).thenAnswer(i -> i.getArgument(0));

        Statement result = statementService.removeBillFromStatement(1L, 10L);

        assertEquals("PAID", result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getBalanceAmount());
    }

    @Test
    void getStatementBills_returnsList() {
        when(invoiceBillRepository.findByStatementId(1L)).thenReturn(List.of(testBill));
        assertEquals(1, statementService.getStatementBills(1L).size());
    }

    @Test
    void deleteStatement_unlinksBillsAndDeletes() {
        testBill.setStatement(testStatement);
        when(statementRepository.findById(1L)).thenReturn(Optional.of(testStatement));
        when(invoiceBillRepository.findByStatementId(1L)).thenReturn(List.of(testBill));

        statementService.deleteStatement(1L);

        assertNull(testBill.getStatement());
        verify(invoiceBillRepository).save(testBill);
        verify(statementRepository).deleteById(1L);
    }

    @Test
    void previewBills_noFilters_returnsUnlinkedBills() {
        when(invoiceBillRepository.findUnlinkedCreditBills(eq(1L), any(), any()))
                .thenReturn(List.of(testBill));

        List<InvoiceBill> result = statementService.previewBills(
                1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null, null);
        assertEquals(1, result.size());
    }

    @Test
    void previewBills_withVehicleFilter_usesVehicleQuery() {
        when(invoiceBillRepository.findUnlinkedCreditBillsByVehicle(eq(1L), any(), any(), eq(5L)))
                .thenReturn(List.of(testBill));

        List<InvoiceBill> result = statementService.previewBills(
                1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 5L, null);
        assertEquals(1, result.size());
    }
}
