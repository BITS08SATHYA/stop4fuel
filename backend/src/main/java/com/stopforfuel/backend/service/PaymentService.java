package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StatementRepository statementRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final CustomerRepository customerRepository;
    private final PaymentModeRepository paymentModeRepository;

    public Page<Payment> getPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }

    public Page<Payment> getPaymentsByCustomer(Long customerId, Pageable pageable) {
        return paymentRepository.findByCustomerId(customerId, pageable);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + id));
    }

    public List<Payment> getPaymentsByCustomer(Long customerId) {
        return paymentRepository.findByCustomerId(customerId);
    }

    public List<Payment> getPaymentsByStatement(Long statementId) {
        return paymentRepository.findByStatementId(statementId);
    }

    public List<Payment> getPaymentsByInvoiceBill(Long invoiceBillId) {
        return paymentRepository.findByInvoiceBillId(invoiceBillId);
    }

    public List<Payment> getPaymentsByShift(Long shiftId) {
        return paymentRepository.findByShiftId(shiftId);
    }

    /**
     * Record a payment against a Statement (for statement customers).
     * Updates statement received/balance amounts and auto-flips status
     * when fully paid. Also marks all underlying bills as PAID.
     */
    @Transactional
    public Payment recordStatementPayment(Long statementId, Payment payment) {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found"));

        if ("PAID".equals(statement.getStatus())) {
            throw new RuntimeException("Statement " + statement.getStatementNo() + " is already fully paid");
        }

        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }

        if (payment.getAmount().compareTo(statement.getBalanceAmount()) > 0) {
            throw new RuntimeException("Payment amount (" + payment.getAmount()
                    + ") exceeds statement balance (" + statement.getBalanceAmount() + ")");
        }

        // Set payment references
        payment.setStatement(statement);
        payment.setCustomer(statement.getCustomer());
        if (payment.getScid() == null) {
            payment.setScid(statement.getScid());
        }

        // Resolve payment mode
        resolvePaymentMode(payment);

        Payment saved = paymentRepository.save(payment);

        // Update statement totals
        BigDecimal totalReceived = paymentRepository.sumPaymentsByStatementId(statementId);
        statement.setReceivedAmount(totalReceived);
        statement.setBalanceAmount(statement.getNetAmount().subtract(totalReceived));

        // Auto-flip to PAID when balance reaches zero
        if (statement.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            statement.setStatus("PAID");
            statement.setBalanceAmount(BigDecimal.ZERO);

            // Mark all underlying credit bills as PAID
            List<InvoiceBill> bills = invoiceBillRepository.findByStatementId(statementId);
            for (InvoiceBill bill : bills) {
                bill.setPaymentStatus("PAID");
                invoiceBillRepository.save(bill);
            }
        }

        statementRepository.save(statement);
        return saved;
    }

    /**
     * Record a payment against an individual InvoiceBill (for local credit customers).
     * Updates bill payment status to PAID when fully settled.
     */
    @Transactional
    public Payment recordBillPayment(Long invoiceBillId, Payment payment) {
        InvoiceBill bill = invoiceBillRepository.findById(invoiceBillId)
                .orElseThrow(() -> new RuntimeException("Invoice bill not found"));

        if (!"CREDIT".equals(bill.getBillType())) {
            throw new RuntimeException("Cannot record payment against a non-credit bill");
        }

        if ("PAID".equals(bill.getPaymentStatus())) {
            throw new RuntimeException("Bill is already fully paid");
        }

        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero");
        }

        // Calculate current balance for this bill
        BigDecimal totalReceived = paymentRepository.sumPaymentsByInvoiceBillId(invoiceBillId);
        BigDecimal billBalance = bill.getNetAmount().subtract(totalReceived);

        if (payment.getAmount().compareTo(billBalance) > 0) {
            throw new RuntimeException("Payment amount (" + payment.getAmount()
                    + ") exceeds bill balance (" + billBalance + ")");
        }

        // Set payment references
        payment.setInvoiceBill(bill);
        payment.setCustomer(bill.getCustomer());
        if (payment.getScid() == null) {
            payment.setScid(bill.getScid());
        }

        // Resolve payment mode
        resolvePaymentMode(payment);

        Payment saved = paymentRepository.save(payment);

        // Check if bill is now fully paid
        BigDecimal newTotalReceived = paymentRepository.sumPaymentsByInvoiceBillId(invoiceBillId);
        if (newTotalReceived.compareTo(bill.getNetAmount()) >= 0) {
            bill.setPaymentStatus("PAID");
            invoiceBillRepository.save(bill);
        }

        return saved;
    }

    /**
     * Get payment history and balance summary for a statement.
     */
    public StatementPaymentSummary getStatementPaymentSummary(Long statementId) {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found"));

        List<Payment> payments = paymentRepository.findByStatementId(statementId);

        StatementPaymentSummary summary = new StatementPaymentSummary();
        summary.statement = statement;
        summary.payments = payments;
        summary.totalReceived = statement.getReceivedAmount();
        summary.balanceAmount = statement.getBalanceAmount();
        return summary;
    }

    /**
     * Get payment history and balance for an individual bill.
     */
    public BillPaymentSummary getBillPaymentSummary(Long invoiceBillId) {
        InvoiceBill bill = invoiceBillRepository.findById(invoiceBillId)
                .orElseThrow(() -> new RuntimeException("Invoice bill not found"));

        List<Payment> payments = paymentRepository.findByInvoiceBillId(invoiceBillId);
        BigDecimal totalReceived = paymentRepository.sumPaymentsByInvoiceBillId(invoiceBillId);
        BigDecimal balance = bill.getNetAmount().subtract(totalReceived);

        BillPaymentSummary summary = new BillPaymentSummary();
        summary.invoiceBill = bill;
        summary.payments = payments;
        summary.totalReceived = totalReceived;
        summary.balanceAmount = balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance;
        return summary;
    }

    /**
     * Resolve payment mode from the entity's paymentMode.id to a managed entity.
     */
    private void resolvePaymentMode(Payment payment) {
        if (payment.getPaymentMode() != null && payment.getPaymentMode().getId() != null) {
            PaymentMode mode = paymentModeRepository.findById(payment.getPaymentMode().getId())
                    .orElseThrow(() -> new RuntimeException("Payment mode not found"));
            payment.setPaymentMode(mode);
        }
    }

    // Summary DTOs (inner classes for simplicity)
    public static class StatementPaymentSummary {
        public Statement statement;
        public List<Payment> payments;
        public BigDecimal totalReceived;
        public BigDecimal balanceAmount;
    }

    public static class BillPaymentSummary {
        public InvoiceBill invoiceBill;
        public List<Payment> payments;
        public BigDecimal totalReceived;
        public BigDecimal balanceAmount;
    }
}
