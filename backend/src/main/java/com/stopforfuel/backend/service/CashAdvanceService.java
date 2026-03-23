package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashAdvance;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.entity.transaction.CashTransaction;
import com.stopforfuel.backend.entity.transaction.ExpenseTransaction;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.DuplicateResourceException;
import com.stopforfuel.backend.repository.CashAdvanceRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.StatementRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CashAdvanceService {

    private final CashAdvanceRepository repository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftService shiftService;
    private final ShiftTransactionService shiftTransactionService;

    public List<CashAdvance> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public CashAdvance getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + id));
    }

    public List<CashAdvance> getByStatus(String status) {
        return repository.findByStatusOrderByAdvanceDateDesc(status);
    }

    public List<CashAdvance> getByShift(Long shiftId) {
        return repository.findByShiftIdOrderByAdvanceDateDesc(shiftId);
    }

    public List<CashAdvance> getByEmployee(Long employeeId) {
        return repository.findByEmployeeIdOrderByAdvanceDateDesc(employeeId);
    }

    public List<CashAdvance> getOutstanding() {
        return repository.findByStatusInOrderByAdvanceDateDesc(List.of("GIVEN", "PARTIALLY_RETURNED"));
    }

    @Transactional
    public CashAdvance create(CashAdvance advance) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            advance.setShiftId(activeShift.getId());
        }

        // Link employee if employeeId is provided via transient field
        if (advance.getEmployee() != null && advance.getEmployee().getId() != null) {
            Employee emp = employeeRepository.findById(advance.getEmployee().getId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            advance.setEmployee(emp);
            if (advance.getRecipientName() == null || advance.getRecipientName().isBlank()) {
                advance.setRecipientName(emp.getName());
            }
            if (advance.getRecipientPhone() == null || advance.getRecipientPhone().isBlank()) {
                advance.setRecipientPhone(emp.getPhone());
            }
        }

        CashAdvance saved = repository.save(advance);

        // Auto-create an ExpenseTransaction to deduct from the shift register
        if (activeShift != null) {
            ExpenseTransaction expTxn = new ExpenseTransaction();
            expTxn.setReceivedAmount(advance.getAmount());
            expTxn.setExpenseAmount(advance.getAmount());
            expTxn.setExpenseDescription("Cash Advance: " + advance.getAdvanceType() + " - " + advance.getRecipientName());
            expTxn.setRemarks("Auto: Advance #" + saved.getId());
            expTxn.setShiftId(activeShift.getId());
            expTxn.setScid(advance.getScid());
            shiftTransactionService.create(expTxn);
        }

        return saved;
    }

    @Transactional
    public CashAdvance recordReturn(Long id, BigDecimal returnedAmount, String returnRemarks) {
        CashAdvance advance = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + id));

        advance.setReturnedAmount(returnedAmount);
        advance.setReturnDate(LocalDateTime.now());
        advance.setReturnRemarks(returnRemarks);

        updateAdvanceStatus(advance);

        CashAdvance updated = repository.save(advance);

        // Create a CashTransaction in the active shift for the returned amount
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null && returnedAmount.compareTo(BigDecimal.ZERO) > 0) {
            CashTransaction cashTxn = new CashTransaction();
            cashTxn.setReceivedAmount(returnedAmount);
            cashTxn.setRemarks("Advance Return #" + id);
            cashTxn.setShiftId(activeShift.getId());
            cashTxn.setScid(advance.getScid());
            shiftTransactionService.create(cashTxn);
        }

        return updated;
    }

    @Transactional
    public CashAdvance assignInvoice(Long advanceId, Long invoiceId) {
        CashAdvance advance = repository.findById(advanceId)
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + advanceId));

        if ("CANCELLED".equals(advance.getStatus()) || "RETURNED".equals(advance.getStatus())) {
            throw new BusinessException("Cannot assign invoices to a " + advance.getStatus().toLowerCase() + " advance");
        }

        InvoiceBill invoice = invoiceBillRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + invoiceId));

        if (invoice.getCashAdvance() != null) {
            throw new DuplicateResourceException("Invoice is already assigned to advance #" + invoice.getCashAdvance().getId());
        }

        invoice.setCashAdvance(advance);
        invoiceBillRepository.save(invoice);

        // Recalculate utilized amount
        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    @Transactional
    public CashAdvance unassignInvoice(Long advanceId, Long invoiceId) {
        CashAdvance advance = repository.findById(advanceId)
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + advanceId));

        InvoiceBill invoice = invoiceBillRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + invoiceId));

        if (invoice.getCashAdvance() == null || !invoice.getCashAdvance().getId().equals(advanceId)) {
            throw new BusinessException("Invoice is not assigned to this advance");
        }

        invoice.setCashAdvance(null);
        invoiceBillRepository.save(invoice);

        // Recalculate utilized amount
        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    public List<InvoiceBill> getAssignedInvoices(Long advanceId) {
        return invoiceBillRepository.findByCashAdvanceId(advanceId);
    }

    @Transactional
    public CashAdvance assignStatement(Long advanceId, Long statementId) {
        CashAdvance advance = repository.findById(advanceId)
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + advanceId));

        if ("CANCELLED".equals(advance.getStatus()) || "RETURNED".equals(advance.getStatus())) {
            throw new BusinessException("Cannot assign statement to a " + advance.getStatus().toLowerCase() + " advance");
        }

        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + statementId));

        advance.setStatement(statement);

        // Include statement amount in utilized calculation
        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    @Transactional
    public CashAdvance unassignStatement(Long advanceId) {
        CashAdvance advance = repository.findById(advanceId)
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + advanceId));

        advance.setStatement(null);

        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    @Transactional
    public CashAdvance cancel(Long id) {
        CashAdvance advance = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + id));
        advance.setStatus("CANCELLED");
        return repository.save(advance);
    }

    @Transactional
    public void delete(Long id) {
        // Unlink any assigned invoices first
        List<InvoiceBill> assignedInvoices = invoiceBillRepository.findByCashAdvanceId(id);
        for (InvoiceBill invoice : assignedInvoices) {
            invoice.setCashAdvance(null);
            invoiceBillRepository.save(invoice);
        }
        repository.deleteById(id);
    }

    private void recalculateUtilizedAmount(CashAdvance advance) {
        BigDecimal utilized = BigDecimal.ZERO;

        // Sum from assigned invoices
        List<InvoiceBill> invoices = invoiceBillRepository.findByCashAdvanceId(advance.getId());
        for (InvoiceBill inv : invoices) {
            if (inv.getNetAmount() != null) {
                utilized = utilized.add(inv.getNetAmount());
            }
        }

        // Add statement net amount if assigned
        if (advance.getStatement() != null && advance.getStatement().getNetAmount() != null) {
            utilized = utilized.add(advance.getStatement().getNetAmount());
        }

        advance.setUtilizedAmount(utilized);
        updateAdvanceStatus(advance);
    }

    private void updateAdvanceStatus(CashAdvance advance) {
        BigDecimal returned = advance.getReturnedAmount() != null ? advance.getReturnedAmount() : BigDecimal.ZERO;
        BigDecimal utilized = advance.getUtilizedAmount() != null ? advance.getUtilizedAmount() : BigDecimal.ZERO;
        BigDecimal settled = returned.add(utilized);

        if (settled.compareTo(advance.getAmount()) >= 0) {
            // If all cash returned: RETURNED, if mix of bills + return: SETTLED
            if (utilized.compareTo(BigDecimal.ZERO) > 0) {
                advance.setStatus("SETTLED");
            } else {
                advance.setStatus("RETURNED");
            }
        } else if (settled.compareTo(BigDecimal.ZERO) > 0) {
            advance.setStatus("PARTIALLY_RETURNED");
        }
        // Don't change status if nothing settled yet (keep GIVEN)
    }
}
