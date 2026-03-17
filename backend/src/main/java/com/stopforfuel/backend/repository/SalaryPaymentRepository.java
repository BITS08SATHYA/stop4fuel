package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.SalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Long> {
    List<SalaryPayment> findByMonthAndYear(Integer month, Integer year);
    List<SalaryPayment> findByEmployeeIdOrderByYearDescMonthDesc(Long employeeId);
    Optional<SalaryPayment> findByEmployeeIdAndMonthAndYear(Long employeeId, Integer month, Integer year);
}
