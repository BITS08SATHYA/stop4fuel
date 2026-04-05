package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import com.stopforfuel.backend.enums.PaymentMode;
import com.stopforfuel.backend.enums.PaymentStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.*;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StatementRepository statementRepository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final CustomerRepository customerRepository;
    private final ShiftService shiftService;
    private final EAdvanceService eAdvanceService;
    private final EAdvanceRepository eAdvanceRepository;
    private final S3StorageService s3StorageService;

    @Transactional(readOnly = true)
    public Page<Payment> getPayments(String categoryType, String paidAgainst,
                                      LocalDateTime fromDate, LocalDateTime toDate,
                                      Pageable pageable) {
        String ct = (categoryType != null && !categoryType.isEmpty()) ? categoryType : null;
        String pa = (paidAgainst != null && !paidAgainst.isEmpty()) ? paidAgainst : null;
        if (ct != null || pa != null || fromDate != null || toDate != null) {
            return paymentRepository.findWithFilters(ct, pa, fromDate, toDate, pageable);
        }
        return paymentRepository.findAllEager(pageable);
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsForExport(String categoryType, String paidAgainst,
                                               LocalDateTime fromDate, LocalDateTime toDate) {
        String ct = (categoryType != null && !categoryType.isEmpty()) ? categoryType : null;
        String pa = (paidAgainst != null && !paidAgainst.isEmpty()) ? paidAgainst : null;
        return paymentRepository.findAllForExport(ct, pa, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public Page<Payment> getPaymentsByCustomer(Long customerId, Pageable pageable) {
        return paymentRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public Payment getPaymentById(Long id) {
        return paymentRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByCustomer(Long customerId) {
        return paymentRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByStatement(Long statementId) {
        return paymentRepository.findByStatementId(statementId);
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByInvoiceBill(Long invoiceBillId) {
        return paymentRepository.findByInvoiceBillId(invoiceBillId);
    }

    @Transactional(readOnly = true)
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
        Statement statement = statementRepository.findByIdForUpdate(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));

        if ("PAID".equals(statement.getStatus())) {
            throw new BusinessException("Statement " + statement.getStatementNo() + " is already fully paid");
        }

        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }

        if (payment.getAmount().compareTo(statement.getBalanceAmount()) > 0) {
            throw new BusinessException("Payment amount (" + payment.getAmount()
                    + ") exceeds statement balance (" + statement.getBalanceAmount() + ")");
        }

        // Set payment references
        payment.setStatement(statement);
        payment.setCustomer(statement.getCustomer());
        if (payment.getScid() == null) {
            payment.setScid(statement.getScid());
        }
        if (payment.getShiftId() == null) {
            Shift activeShift = shiftService.getActiveShift();
            if (activeShift != null) {
                payment.setShiftId(activeShift.getId());
            }
        }

        // Set employee
        resolveReceivedBy(payment);

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
                bill.setPaymentStatus(PaymentStatus.PAID);
                invoiceBillRepository.save(bill);
            }
        }

        statementRepository.save(statement);

        // Auto-create shift transaction for this payment
        autoCreateShiftTransaction(saved);

        return saved;
    }

    /**
     * Record a payment against an individual InvoiceBill (for local credit customers).
     * Updates bill payment status to PAID when fully settled.
     */
    @Transactional
    public Payment recordBillPayment(Long invoiceBillId, Payment payment) {
        InvoiceBill bill = invoiceBillRepository.findByIdForUpdate(invoiceBillId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice bill not found"));

        if (bill.getBillType() != com.stopforfuel.backend.enums.BillType.CREDIT) {
            throw new BusinessException("Cannot record payment against a non-credit bill");
        }

        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Bill is already fully paid");
        }

        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }

        // Calculate current balance for this bill
        BigDecimal totalReceived = paymentRepository.sumPaymentsByInvoiceBillId(invoiceBillId);
        BigDecimal billBalance = bill.getNetAmount().subtract(totalReceived);

        if (payment.getAmount().compareTo(billBalance) > 0) {
            throw new BusinessException("Payment amount (" + payment.getAmount()
                    + ") exceeds bill balance (" + billBalance + ")");
        }

        // Set payment references
        payment.setInvoiceBill(bill);
        payment.setCustomer(bill.getCustomer());
        if (payment.getScid() == null) {
            payment.setScid(bill.getScid());
        }
        if (payment.getShiftId() == null) {
            Shift activeShift = shiftService.getActiveShift();
            if (activeShift != null) {
                payment.setShiftId(activeShift.getId());
            }
        }

        // Set employee
        resolveReceivedBy(payment);

        Payment saved = paymentRepository.save(payment);

        // Check if bill is now fully paid
        BigDecimal newTotalReceived = paymentRepository.sumPaymentsByInvoiceBillId(invoiceBillId);
        if (newTotalReceived.compareTo(bill.getNetAmount()) >= 0) {
            bill.setPaymentStatus(PaymentStatus.PAID);
            invoiceBillRepository.save(bill);
        }

        // Auto-create shift transaction for this payment
        autoCreateShiftTransaction(saved);

        return saved;
    }

    /**
     * Get payment history and balance summary for a statement.
     */
    @Transactional(readOnly = true)
    public StatementPaymentSummary getStatementPaymentSummary(Long statementId) {
        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));

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
    @Transactional(readOnly = true)
    public BillPaymentSummary getBillPaymentSummary(Long invoiceBillId) {
        InvoiceBill bill = invoiceBillRepository.findById(invoiceBillId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice bill not found"));

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
     * Delete a payment and reverse all side effects:
     * - Revert statement received/balance amounts and status
     * - Revert bill payment status
     * - Remove linked EAdvance record
     * - Delete proof image from S3
     */
    @Transactional
    public void deletePayment(Long id) {
        Payment payment = paymentRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));

        // 1. Remove linked EAdvance if exists
        eAdvanceRepository.findByPaymentId(id).ifPresent(eAdv -> eAdvanceService.delete(eAdv.getId()));

        // 2. Delete proof image from S3 if exists
        if (payment.getProofImageKey() != null) {
            s3StorageService.delete(payment.getProofImageKey());
        }

        // 3. Reverse statement side effects
        if (payment.getStatement() != null) {
            Statement statement = payment.getStatement();

            // Delete the payment first so the sum query excludes it
            paymentRepository.delete(payment);
            paymentRepository.flush();

            // Recalculate statement totals from remaining payments
            BigDecimal totalReceived = paymentRepository.sumPaymentsByStatementId(statement.getId());
            statement.setReceivedAmount(totalReceived);
            statement.setBalanceAmount(statement.getNetAmount().subtract(totalReceived));

            // If it was PAID, reset to NOT_PAID
            if ("PAID".equals(statement.getStatus())) {
                statement.setStatus("NOT_PAID");

                // Reset all underlying bills back to NOT_PAID
                List<InvoiceBill> bills = invoiceBillRepository.findByStatementId(statement.getId());
                for (InvoiceBill bill : bills) {
                    if (bill.getPaymentStatus() == PaymentStatus.PAID) {
                        bill.setPaymentStatus(PaymentStatus.NOT_PAID);
                        invoiceBillRepository.save(bill);
                    }
                }
            }

            statementRepository.save(statement);

        } else if (payment.getInvoiceBill() != null) {
            // 4. Reverse direct bill payment side effects
            InvoiceBill bill = payment.getInvoiceBill();

            // Delete the payment first so the sum query excludes it
            paymentRepository.delete(payment);
            paymentRepository.flush();

            // Recalculate total received from remaining payments
            BigDecimal remainingReceived = paymentRepository.sumPaymentsByInvoiceBillId(bill.getId());

            // If bill was PAID but remaining payments no longer cover the full amount, reset
            if (bill.getPaymentStatus() == PaymentStatus.PAID
                    && remainingReceived.compareTo(bill.getNetAmount()) < 0) {
                bill.setPaymentStatus(PaymentStatus.NOT_PAID);
                invoiceBillRepository.save(bill);
            }

        } else {
            // No statement or bill linked — just delete the payment
            paymentRepository.delete(payment);
        }
    }

    /**
     * Auto-creates an EAdvance entry when a credit payment is made via electronic mode.
     * Cash payments need no separate record — the Payment itself is the source of truth.
     */
    private void autoCreateShiftTransaction(Payment payment) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            return;
        }

        BigDecimal amount = payment.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        PaymentMode mode = payment.getPaymentMode();
        if (mode == null || mode == PaymentMode.CASH || mode == PaymentMode.NEFT) {
            return;
        }

        // Create EAdvance for electronic payment modes (CARD, UPI, CHEQUE, CCMS, BANK_TRANSFER)
        String customerName = payment.getCustomer() != null ? payment.getCustomer().getName() : null;
        String remark = "Auto: Payment #" + payment.getId()
                + (customerName != null ? " - " + customerName : "");
        EAdvance eAdv = new EAdvance();
        eAdv.setAmount(amount);
        eAdv.setRemarks(remark);
        eAdv.setShiftId(activeShift.getId());
        eAdv.setScid(payment.getScid());
        eAdv.setAdvanceType(mode);
        eAdv.setPayment(payment);
        eAdv.setStatement(payment.getStatement());
        if (mode == PaymentMode.CARD && customerName != null) {
            eAdv.setCustomerName(customerName);
        }
        eAdvanceService.create(eAdv);
    }

    /**
     * Set receivedBy from the active shift's attendant if not already set.
     */
    private void resolveReceivedBy(Payment payment) {
        if (payment.getReceivedBy() == null) {
            Shift activeShift = shiftService.getActiveShift();
            if (activeShift != null && activeShift.getAttendant() != null) {
                payment.setReceivedBy(activeShift.getAttendant());
            }
        }
    }

    /**
     * Upload a proof image for a payment (cheque scan, UPI screenshot, etc.).
     */
    @Transactional
    public Payment uploadProofImage(Long paymentId, MultipartFile file) throws IOException {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));

        // Delete old proof if exists
        if (payment.getProofImageKey() != null) {
            s3StorageService.delete(payment.getProofImageKey());
        }

        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String key = "payments/" + paymentId + "/proof-" + System.currentTimeMillis() + ext;

        s3StorageService.upload(key, file);
        payment.setProofImageKey(key);
        return paymentRepository.save(payment);
    }

    /**
     * Get a presigned URL for a payment's proof image.
     */
    @Transactional(readOnly = true)
    public String getProofImageUrl(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));
        if (payment.getProofImageKey() == null) {
            return null;
        }
        return s3StorageService.getPresignedUrl(payment.getProofImageKey());
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
