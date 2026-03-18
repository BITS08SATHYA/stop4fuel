package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashInflowRepayment;
import com.stopforfuel.backend.entity.ExternalCashInflow;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.transaction.CashTransaction;
import com.stopforfuel.backend.entity.transaction.ExpenseTransaction;
import com.stopforfuel.backend.repository.CashInflowRepaymentRepository;
import com.stopforfuel.backend.repository.ExternalCashInflowRepository;
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
    private final ShiftTransactionService shiftTransactionService;

    public List<ExternalCashInflow> getAll() {
        return inflowRepository.findAllByOrderByInflowDateDesc();
    }

    public List<ExternalCashInflow> getByShift(Long shiftId) {
        return inflowRepository.findByShiftIdOrderByInflowDateDesc(shiftId);
    }

    public List<ExternalCashInflow> getByStatus(String status) {
        return inflowRepository.findByStatusOrderByInflowDateDesc(status);
    }

    @Transactional
    public ExternalCashInflow create(ExternalCashInflow inflow) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            inflow.setShiftId(activeShift.getId());
        }

        ExternalCashInflow saved = inflowRepository.save(inflow);

        // Auto-create CashTransaction (money coming IN to the register)
        if (activeShift != null) {
            CashTransaction cashTxn = new CashTransaction();
            cashTxn.setReceivedAmount(inflow.getAmount());
            cashTxn.setRemarks("Auto: External Cash Inflow #" + saved.getId() + " - " + inflow.getSource());
            cashTxn.setShiftId(activeShift.getId());
            cashTxn.setScid(inflow.getScid());
            shiftTransactionService.create(cashTxn);
        }

        return saved;
    }

    @Transactional
    public CashInflowRepayment recordRepayment(Long inflowId, CashInflowRepayment repayment) {
        ExternalCashInflow inflow = inflowRepository.findById(inflowId)
                .orElseThrow(() -> new RuntimeException("External cash inflow not found with id: " + inflowId));

        repayment.setCashInflow(inflow);
        if (repayment.getScid() == null) {
            repayment.setScid(inflow.getScid());
        }

        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            repayment.setShiftId(activeShift.getId());
        }

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

        // Auto-create ExpenseTransaction (money going OUT of the register)
        if (activeShift != null) {
            ExpenseTransaction expTxn = new ExpenseTransaction();
            expTxn.setReceivedAmount(repayment.getAmount());
            expTxn.setExpenseAmount(repayment.getAmount());
            expTxn.setExpenseDescription("Cash Inflow Repayment - " + inflow.getSource());
            expTxn.setRemarks("Auto: Inflow Repayment #" + saved.getId());
            expTxn.setShiftId(activeShift.getId());
            expTxn.setScid(inflow.getScid());
            shiftTransactionService.create(expTxn);
        }

        return saved;
    }

    public List<CashInflowRepayment> getRepayments(Long inflowId) {
        return repaymentRepository.findByCashInflowIdOrderByRepaymentDateDesc(inflowId);
    }
}
