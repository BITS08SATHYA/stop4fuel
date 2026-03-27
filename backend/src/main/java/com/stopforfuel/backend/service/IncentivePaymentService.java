package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.IncentivePayment;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.IncentivePaymentRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncentivePaymentService {

    private final IncentivePaymentRepository repository;
    private final ShiftService shiftService;

    public List<IncentivePayment> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public IncentivePayment getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Incentive payment not found with id: " + id));
    }

    public List<IncentivePayment> getByShift(Long shiftId) {
        return repository.findByShiftIdOrderByPaymentDateDesc(shiftId);
    }

    public List<IncentivePayment> getByCustomer(Long customerId) {
        return repository.findByCustomerIdOrderByPaymentDateDesc(customerId);
    }

    @Transactional
    public IncentivePayment create(IncentivePayment payment) {
        Shift activeShift = shiftService.getActiveShift();
        if (activeShift != null) {
            payment.setShiftId(activeShift.getId());
        }
        return repository.save(payment);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public BigDecimal sumByShift(Long shiftId) {
        return repository.sumByShift(shiftId);
    }
}
