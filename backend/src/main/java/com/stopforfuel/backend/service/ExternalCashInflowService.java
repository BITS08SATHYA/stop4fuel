package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashInflowRepayment;
import com.stopforfuel.backend.entity.ExternalCashInflow;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.Expense;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.CashInflowRepaymentRepository;
import com.stopforfuel.backend.repository.ExternalCashInflowRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalCashInflowService {

    private final ExternalCashInflowRepository inflowRepository;
    private final CashInflowRepaymentRepository repaymentRepository;
    private final ShiftService shiftService;
    private final ExpenseService expenseService;

    @Transactional(readOnly = true)
    public List<ExternalCashInflow> getAll() {
        return inflowRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<ExternalCashInflow> getByShift(Long shiftId) {
        return inflowRepository.findByShiftIdOrderByInflowDateDesc(shiftId);
    }

    @Transactional(readOnly = true)
    public List<ExternalCashInflow> getByStatus(String status) {
        return inflowRepository.findByStatusAndScidOrderByInflowDateDesc(status, SecurityUtils.getScid());
    }

    @Transactional
    public ExternalCashInflow create(ExternalCashInflow inflow) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            throw new BusinessException("No active shift. Please open a shift before recording an external cash inflow.");
        }
        inflow.setShiftId(activeShift.getId());

        ExternalCashInflow saved = inflowRepository.save(inflow);

        // No separate CashTransaction needed — ExternalCashInflow is the source of truth

        return saved;
    }

    @Transactional
    public CashInflowRepayment recordRepayment(Long inflowId, CashInflowRepayment repayment) {
        ExternalCashInflow inflow = inflowRepository.findByIdAndScidForUpdate(inflowId, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("External cash inflow not found with id: " + inflowId));

        repayment.setCashInflow(inflow);
        if (repayment.getScid() == null) {
            repayment.setScid(inflow.getScid());
        }

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            throw new BusinessException("No active shift. Please open a shift before recording a repayment.");
        }
        repayment.setShiftId(activeShift.getId());

        CashInflowRepayment saved = repaymentRepository.save(repayment);

        // Update parent inflow
        BigDecimal newRepaid = inflow.getRepaidAmount().add(repayment.getAmount());
        inflow.setRepaidAmount(newRepaid);
        if (newRepaid.compareTo(inflow.getAmount()) >= 0) {
            inflow.setStatus("FULLY_REPAID");
        } else {
            inflow.setStatus("PARTIALLY_REPAID");
        }
        inflowRepository.save(inflow);

        // Auto-create Expense (money going OUT of the register)
        Expense expense = new Expense();
        expense.setAmount(repayment.getAmount());
        expense.setDescription("Cash Inflow Repayment - " + inflow.getSource());
        expense.setRemarks("Auto: Inflow Repayment #" + saved.getId());
        expense.setShiftId(activeShift.getId());
        expense.setScid(inflow.getScid());
        expenseService.create(expense);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CashInflowRepayment> getRepayments(Long inflowId) {
        return repaymentRepository.findByCashInflowIdOrderByRepaymentDateDesc(inflowId);
    }
}
