package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.SalaryPayment;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryPaymentRepository extends ScidRepository<SalaryPayment> {
    List<SalaryPayment> findByMonthAndYear(Integer month, Integer year);
    List<SalaryPayment> findByShiftId(Long shiftId);
    List<SalaryPayment> findByEmployeeIdOrderByYearDescMonthDesc(Long employeeId);
    Optional<SalaryPayment> findByEmployeeIdAndMonthAndYear(Long employeeId, Integer month, Integer year);
}
