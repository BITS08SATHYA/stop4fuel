package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.InvoiceBill;
import com.stopforfuel.backend.entity.OperationalAdvance;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.DuplicateResourceException;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.InvoiceBillRepository;
import com.stopforfuel.backend.repository.OperationalAdvanceRepository;
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
public class OperationalAdvanceService {

    private final OperationalAdvanceRepository repository;
    private final InvoiceBillRepository invoiceBillRepository;
    private final StatementRepository statementRepository;
    private final EmployeeRepository employeeRepository;
    private final ShiftService shiftService;

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public OperationalAdvance getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Operational advance not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getByStatus(String status) {
        return repository.findByStatusAndScidOrderByAdvanceDateDesc(com.stopforfuel.backend.enums.AdvanceStatus.valueOf(status), SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getByShift(Long shiftId) {
        return repository.findByShiftIdOrderByIdDesc(shiftId);
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getByEmployee(Long employeeId) {
        return repository.findByEmployeeIdAndScidOrderByAdvanceDateDesc(employeeId, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getByType(String advanceType) {
        return repository.findByAdvanceTypeAndScidOrderByAdvanceDateDesc(com.stopforfuel.backend.enums.AdvanceType.valueOf(advanceType), SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getByDateRange(java.time.LocalDate fromDate, java.time.LocalDate toDate) {
        java.time.LocalDateTime from = fromDate.atStartOfDay();
        java.time.LocalDateTime to = toDate.atTime(java.time.LocalTime.MAX);
        return repository.findByDateRange(SecurityUtils.getScid(), from, to);
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getOutstanding() {
        return repository.findByStatusInAndScidOrderByAdvanceDateDesc(List.of(com.stopforfuel.backend.enums.AdvanceStatus.GIVEN, com.stopforfuel.backend.enums.AdvanceStatus.PARTIALLY_RETURNED), SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<OperationalAdvance> getPendingByEmployee(Long employeeId) {
        return repository.findByEmployeeIdAndStatusAndScid(employeeId, com.stopforfuel.backend.enums.AdvanceStatus.PENDING, SecurityUtils.getScid());
    }

    @Transactional
    public OperationalAdvance create(OperationalAdvance advance) {
        validateCashDestination(advance);

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            advance.setShiftId(activeShift.getId());
        }

        // Link employee if employeeId is provided
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

        return repository.save(advance);
    }

    /** CASH-type advances must declare whether the cash was bank-deposited or spent. */
    private void validateCashDestination(OperationalAdvance advance) {
        if (advance.getAdvanceType() == com.stopforfuel.backend.enums.AdvanceType.CASH
                && advance.getCashDestination() == null) {
            throw new com.stopforfuel.backend.exception.BusinessException(
                    "cashDestination is required for CASH advances (BANK_DEPOSIT or SPENT)");
        }
        // For non-CASH advances, force-null the destination to avoid stale data
        if (advance.getAdvanceType() != com.stopforfuel.backend.enums.AdvanceType.CASH) {
            advance.setCashDestination(null);
        }
    }

    @Transactional
    public OperationalAdvance recordReturn(Long id, BigDecimal returnedAmount, String returnRemarks) {
        OperationalAdvance advance = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Operational advance not found with id: " + id));

        advance.setReturnedAmount(returnedAmount);
        advance.setReturnDate(LocalDateTime.now());
        advance.setReturnRemarks(returnRemarks);

        updateAdvanceStatus(advance);

        return repository.save(advance);
    }

    @Transactional
    public OperationalAdvance assignInvoice(Long advanceId, Long invoiceId) {
        OperationalAdvance advance = repository.findByIdForUpdate(advanceId)
                .orElseThrow(() -> new RuntimeException("Operational advance not found with id: " + advanceId));

        if (advance.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.CANCELLED || advance.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.RETURNED) {
            throw new BusinessException("Cannot assign invoices to a " + advance.getStatus().name().toLowerCase() + " advance");
        }

        InvoiceBill invoice = invoiceBillRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + invoiceId));

        if (invoice.getOperationalAdvance() != null) {
            throw new DuplicateResourceException("Invoice is already assigned to advance #" + invoice.getOperationalAdvance().getId());
        }

        invoice.setOperationalAdvance(advance);
        invoiceBillRepository.save(invoice);

        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    @Transactional
    public OperationalAdvance unassignInvoice(Long advanceId, Long invoiceId) {
        OperationalAdvance advance = repository.findByIdForUpdate(advanceId)
                .orElseThrow(() -> new RuntimeException("Operational advance not found with id: " + advanceId));

        InvoiceBill invoice = invoiceBillRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found with id: " + invoiceId));

        if (invoice.getOperationalAdvance() == null || !invoice.getOperationalAdvance().getId().equals(advanceId)) {
            throw new BusinessException("Invoice is not assigned to this advance");
        }

        invoice.setOperationalAdvance(null);
        invoiceBillRepository.save(invoice);

        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    @Transactional(readOnly = true)
    public List<InvoiceBill> getAssignedInvoices(Long advanceId) {
        return invoiceBillRepository.findByOperationalAdvanceId(advanceId);
    }

    @Transactional
    public OperationalAdvance assignStatement(Long advanceId, Long statementId) {
        OperationalAdvance advance = repository.findByIdForUpdate(advanceId)
                .orElseThrow(() -> new RuntimeException("Operational advance not found with id: " + advanceId));

        if (advance.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.CANCELLED || advance.getStatus() == com.stopforfuel.backend.enums.AdvanceStatus.RETURNED) {
            throw new BusinessException("Cannot assign statement to a " + advance.getStatus().name().toLowerCase() + " advance");
        }

        Statement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new RuntimeException("Statement not found with id: " + statementId));

        advance.setStatement(statement);
        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    @Transactional
    public OperationalAdvance unassignStatement(Long advanceId) {
        OperationalAdvance advance = repository.findByIdForUpdate(advanceId)
                .orElseThrow(() -> new RuntimeException("Operational advance not found with id: " + advanceId));

        advance.setStatement(null);
        recalculateUtilizedAmount(advance);

        return repository.save(advance);
    }

    @Transactional
    public OperationalAdvance updateStatus(Long id, String status) {
        OperationalAdvance advance = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Operational advance not found with id: " + id));
        advance.setStatus(com.stopforfuel.backend.enums.AdvanceStatus.valueOf(status));
        return repository.save(advance);
    }

    @Transactional
    public OperationalAdvance cancel(Long id) {
        return updateStatus(id, "CANCELLED");
    }

    @Transactional
    public void delete(Long id) {
        List<InvoiceBill> assignedInvoices = invoiceBillRepository.findByOperationalAdvanceId(id);
        for (InvoiceBill invoice : assignedInvoices) {
            invoice.setOperationalAdvance(null);
            invoiceBillRepository.save(invoice);
        }
        repository.deleteById(id);
    }

    private void recalculateUtilizedAmount(OperationalAdvance advance) {
        BigDecimal utilized = BigDecimal.ZERO;

        List<InvoiceBill> invoices = invoiceBillRepository.findByOperationalAdvanceId(advance.getId());
        for (InvoiceBill inv : invoices) {
            if (inv.getNetAmount() != null) {
                utilized = utilized.add(inv.getNetAmount());
            }
        }

        if (advance.getStatement() != null && advance.getStatement().getNetAmount() != null) {
            utilized = utilized.add(advance.getStatement().getNetAmount());
        }

        advance.setUtilizedAmount(utilized);
        updateAdvanceStatus(advance);
    }

    private void updateAdvanceStatus(OperationalAdvance advance) {
        BigDecimal returned = advance.getReturnedAmount() != null ? advance.getReturnedAmount() : BigDecimal.ZERO;
        BigDecimal utilized = advance.getUtilizedAmount() != null ? advance.getUtilizedAmount() : BigDecimal.ZERO;
        BigDecimal settled = returned.add(utilized);

        if (settled.compareTo(advance.getAmount()) >= 0) {
            if (utilized.compareTo(BigDecimal.ZERO) > 0) {
                advance.setStatus(com.stopforfuel.backend.enums.AdvanceStatus.SETTLED);
            } else {
                advance.setStatus(com.stopforfuel.backend.enums.AdvanceStatus.RETURNED);
            }
        } else if (settled.compareTo(BigDecimal.ZERO) > 0) {
            advance.setStatus(com.stopforfuel.backend.enums.AdvanceStatus.PARTIALLY_RETURNED);
        }
    }
}
