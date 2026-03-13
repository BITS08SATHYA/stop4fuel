package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.CashAdvance;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.transaction.CashTransaction;
import com.stopforfuel.backend.entity.transaction.ExpenseTransaction;
import com.stopforfuel.backend.repository.CashAdvanceRepository;
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
    private final ShiftService shiftService;
    private final ShiftTransactionService shiftTransactionService;

    public List<CashAdvance> getAll() {
        return repository.findAllByOrderByAdvanceDateDesc();
    }

    public List<CashAdvance> getByStatus(String status) {
        return repository.findByStatusOrderByAdvanceDateDesc(status);
    }

    public List<CashAdvance> getByShift(Long shiftId) {
        return repository.findByShiftIdOrderByAdvanceDateDesc(shiftId);
    }

    @Transactional
    public CashAdvance create(CashAdvance advance) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            advance.setShiftId(activeShift.getId());
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

        if (returnedAmount.compareTo(advance.getAmount()) >= 0) {
            advance.setStatus("RETURNED");
        } else if (returnedAmount.compareTo(BigDecimal.ZERO) > 0) {
            advance.setStatus("PARTIALLY_RETURNED");
        }

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
    public CashAdvance cancel(Long id) {
        CashAdvance advance = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cash advance not found with id: " + id));
        advance.setStatus("CANCELLED");
        return repository.save(advance);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
