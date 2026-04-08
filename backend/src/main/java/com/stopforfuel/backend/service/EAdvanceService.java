package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.EAdvance;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.enums.PaymentMode;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.EAdvanceRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EAdvanceService {

    private final EAdvanceRepository repository;
    private final ShiftService shiftService;

    @Transactional(readOnly = true)
    public List<EAdvance> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public EAdvance getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("E-Advance not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<EAdvance> getByShift(Long shiftId) {
        return repository.findByShiftIdOrderByIdDesc(shiftId);
    }

    @Transactional(readOnly = true)
    public List<EAdvance> getByType(String advanceType) {
        return repository.findByAdvanceTypeAndScidOrderByTransactionDateDesc(PaymentMode.valueOf(advanceType), SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<EAdvance> getByDateRange(LocalDate fromDate, LocalDate toDate, String type) {
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);
        Long scid = SecurityUtils.getScid();
        if (type != null && !type.isBlank()) {
            return repository.findByDateRangeAndType(scid, from, to, PaymentMode.valueOf(type));
        }
        return repository.findByDateRange(scid, from, to);
    }

    @Transactional
    public EAdvance create(EAdvance eAdvance) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            throw new BusinessException("No active shift. Open a shift before creating entries.");
        }
        eAdvance.setShiftId(activeShift.getId());
        return repository.save(eAdvance);
    }

    @Transactional
    public EAdvance update(Long id, EAdvance updated) {
        EAdvance existing = getById(id);
        existing.setAmount(updated.getAmount());
        existing.setAdvanceType(updated.getAdvanceType());
        existing.setRemarks(updated.getRemarks());
        existing.setBatchId(updated.getBatchId());
        existing.setTid(updated.getTid());
        existing.setCustomerName(updated.getCustomerName());
        existing.setCustomerPhone(updated.getCustomerPhone());
        existing.setCardLast4Digit(updated.getCardLast4Digit());
        existing.setBankName(updated.getBankName());
        existing.setChequeNo(updated.getChequeNo());
        existing.setChequeDate(updated.getChequeDate());
        existing.setInFavorOf(updated.getInFavorOf());
        existing.setCcmsNumber(updated.getCcmsNumber());
        existing.setUpiCompany(updated.getUpiCompany());
        existing.setStatement(updated.getStatement());
        return repository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumByShift(Long shiftId) {
        return repository.sumAllByShift(shiftId);
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getShiftSummary(Long shiftId) {
        return Map.of(
            "card", repository.sumByShiftAndType(shiftId, PaymentMode.CARD),
            "upi", repository.sumByShiftAndType(shiftId, PaymentMode.UPI),
            "cheque", repository.sumByShiftAndType(shiftId, PaymentMode.CHEQUE),
            "ccms", repository.sumByShiftAndType(shiftId, PaymentMode.CCMS),
            "bank_transfer", repository.sumByShiftAndType(shiftId, PaymentMode.BANK_TRANSFER),
            "total", repository.sumAllByShift(shiftId)
        );
    }
}
