package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.IncentivePayment;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.IncentivePaymentRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncentivePaymentService {

    private final IncentivePaymentRepository repository;
    private final ShiftService shiftService;

    @Transactional(readOnly = true)
    public List<IncentivePayment> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public IncentivePayment getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Incentive payment not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<IncentivePayment> getByShift(Long shiftId) {
        return repository.findByShiftIdOrderByIdDesc(shiftId);
    }

    @Transactional(readOnly = true)
    public List<IncentivePayment> getByCustomer(Long customerId) {
        return repository.findByCustomerIdOrderByPaymentDateDesc(customerId);
    }

    @Transactional
    public IncentivePayment create(IncentivePayment payment) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift == null) {
            throw new BusinessException("No active shift. Please open a shift before recording an incentive payment.");
        }
        payment.setShiftId(activeShift.getId());
        return repository.save(payment);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumByShift(Long shiftId) {
        return repository.sumByShift(shiftId);
    }

    @Transactional(readOnly = true)
    public List<IncentivePayment> getByDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);
        return repository.findByDateRange(SecurityUtils.getScid(), from, to);
    }
}
