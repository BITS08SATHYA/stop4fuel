package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CreditPolicy;
import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.CustomerBlockEvent;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.CustomerBlockEventRepository;
import com.stopforfuel.backend.repository.CustomerRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.PaymentRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreditMonitoringServiceAutoUnblockTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private InvoiceBillRepository invoiceBillRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StatementRepository statementRepository;
    @Mock private CustomerBlockEventRepository blockEventRepository;
    @Mock private CreditPolicyService creditPolicyService;

    @InjectMocks private CreditMonitoringService service;

    private Customer blockedCustomer;
    private CreditPolicy defaultPolicy;

    @BeforeEach
    void setUp() {
        blockedCustomer = new Customer();
        blockedCustomer.setId(273L);
        blockedCustomer.setScid(1L);
        blockedCustomer.setStatus(EntityStatus.BLOCKED);
        blockedCustomer.setCreditLimitAmount(new BigDecimal("100000"));
        blockedCustomer.setCreditLimitLiters(new BigDecimal("600"));
        blockedCustomer.setConsumedLiters(new BigDecimal("396"));

        defaultPolicy = new CreditPolicy();
        defaultPolicy.setAgingBlockDays(90);
        defaultPolicy.setAgingWatchDays(60);
        defaultPolicy.setUtilizationBlockPercent(100);
        defaultPolicy.setUtilizationWarnPercent(80);
        defaultPolicy.setAutoBlockEnabled(true);
    }

    private void stubAllGatesPass() {
        // Ledger balance well under limit, no unpaid bills, liter consumption under limit.
        when(invoiceBillRepository.sumAllCreditBillsByCustomer(273L)).thenReturn(new BigDecimal("52143.41"));
        when(paymentRepository.sumAllPaymentsByCustomer(273L)).thenReturn(BigDecimal.ZERO);
        when(invoiceBillRepository.findOldestUnpaidBillDate(273L)).thenReturn(Optional.empty());
        when(creditPolicyService.getEffectivePolicy(blockedCustomer)).thenReturn(defaultPolicy);
    }

    @Test
    void tryAutoUnblock_autoBlockedCustomerWithClearedGates_unblocks() {
        CustomerBlockEvent autoEvent = new CustomerBlockEvent();
        autoEvent.setId(10L);
        autoEvent.setEventType("BLOCKED");
        autoEvent.setTriggerType("AUTO_SCHEDULED");
        when(blockEventRepository.findByCustomerIdOrderByCreatedAtDesc(273L))
                .thenReturn(List.of(autoEvent));
        stubAllGatesPass();

        boolean unblocked = service.tryAutoUnblock(blockedCustomer, "AUTO_RESOLVED");

        assertTrue(unblocked);
        assertEquals(EntityStatus.ACTIVE, blockedCustomer.getStatus());
        verify(customerRepository).save(blockedCustomer);

        ArgumentCaptor<CustomerBlockEvent> captor = ArgumentCaptor.forClass(CustomerBlockEvent.class);
        verify(blockEventRepository).save(captor.capture());
        CustomerBlockEvent saved = captor.getValue();
        assertEquals("UNBLOCKED", saved.getEventType());
        assertEquals("AUTO_RESOLVED", saved.getTriggerType());
        assertEquals("BLOCKED", saved.getPreviousStatus());
    }

    @Test
    void tryAutoUnblock_manuallyBlockedCustomer_staysBlockedEvenWhenGatesClear() {
        CustomerBlockEvent manualEvent = new CustomerBlockEvent();
        manualEvent.setId(20L);
        manualEvent.setEventType("BLOCKED");
        manualEvent.setTriggerType("MANUAL");
        when(blockEventRepository.findByCustomerIdOrderByCreatedAtDesc(273L))
                .thenReturn(List.of(manualEvent));

        boolean unblocked = service.tryAutoUnblock(blockedCustomer, "AUTO_RESOLVED");

        assertFalse(unblocked);
        assertEquals(EntityStatus.BLOCKED, blockedCustomer.getStatus());
        verify(customerRepository, never()).save(any());
        verify(blockEventRepository, never()).save(any());
    }

    @Test
    void tryAutoUnblock_utilizationStillCritical_doesNotUnblock() {
        CustomerBlockEvent autoEvent = new CustomerBlockEvent();
        autoEvent.setId(30L);
        autoEvent.setEventType("BLOCKED");
        autoEvent.setTriggerType("AUTO_SCHEDULED");
        when(blockEventRepository.findByCustomerIdOrderByCreatedAtDesc(273L))
                .thenReturn(List.of(autoEvent));
        // Ledger balance still over limit (120k > 100k)
        when(invoiceBillRepository.sumAllCreditBillsByCustomer(273L)).thenReturn(new BigDecimal("120000"));
        when(paymentRepository.sumAllPaymentsByCustomer(273L)).thenReturn(BigDecimal.ZERO);
        when(creditPolicyService.getEffectivePolicy(blockedCustomer)).thenReturn(defaultPolicy);

        boolean unblocked = service.tryAutoUnblock(blockedCustomer, "AUTO_RESOLVED");

        assertFalse(unblocked);
        assertEquals(EntityStatus.BLOCKED, blockedCustomer.getStatus());
        verify(customerRepository, never()).save(any());
    }

    @Test
    void tryAutoUnblock_customerNotBlocked_returnsFalse() {
        blockedCustomer.setStatus(EntityStatus.ACTIVE);

        boolean unblocked = service.tryAutoUnblock(blockedCustomer, "AUTO_PAYMENT");

        assertFalse(unblocked);
        verifyNoInteractions(blockEventRepository);
        verifyNoInteractions(creditPolicyService);
    }

    @Test
    void tryAutoUnblock_silentReBlockAfterManualUnblock_unblocks() {
        // Legacy data: customer was MANUAL-blocked, then unblocked, then silently
        // re-blocked by InvoiceBillService (no event recorded, so latest event is
        // still UNBLOCKED). We treat that as auto and proceed to evaluate gates.
        CustomerBlockEvent oldManualBlock = new CustomerBlockEvent();
        oldManualBlock.setId(40L);
        oldManualBlock.setEventType("BLOCKED");
        oldManualBlock.setTriggerType("MANUAL");
        oldManualBlock.setCreatedAt(LocalDateTime.now().minusDays(30));

        CustomerBlockEvent unblockEvent = new CustomerBlockEvent();
        unblockEvent.setId(41L);
        unblockEvent.setEventType("UNBLOCKED");
        unblockEvent.setTriggerType("MANUAL");
        unblockEvent.setCreatedAt(LocalDateTime.now().minusDays(20));

        // Latest event in the desc-ordered list is UNBLOCKED, so the MANUAL
        // BLOCKED is no longer current.
        when(blockEventRepository.findByCustomerIdOrderByCreatedAtDesc(273L))
                .thenReturn(List.of(unblockEvent, oldManualBlock));
        stubAllGatesPass();

        boolean unblocked = service.tryAutoUnblock(blockedCustomer, "AUTO_PAYMENT");

        assertTrue(unblocked);
        assertEquals(EntityStatus.ACTIVE, blockedCustomer.getStatus());
    }

    @Test
    void tryAutoUnblock_noCreditLimit_doesNotUnblock() {
        blockedCustomer.setCreditLimitAmount(BigDecimal.ZERO);
        when(blockEventRepository.findByCustomerIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());

        boolean unblocked = service.tryAutoUnblock(blockedCustomer, "AUTO_RESOLVED");

        assertFalse(unblocked);
        verifyNoInteractions(creditPolicyService);
    }
}
