package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.SalaryPayment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryPaymentRepository extends ScidRepository<SalaryPayment> {
    @Query("SELECT sp FROM SalaryPayment sp JOIN FETCH sp.employee WHERE sp.month = :month AND sp.year = :year")
    List<SalaryPayment> findByMonthAndYear(@Param("month") Integer month, @Param("year") Integer year);
    List<SalaryPayment> findByShiftId(Long shiftId);
    List<SalaryPayment> findByEmployeeIdOrderByYearDescMonthDesc(Long employeeId);
    Optional<SalaryPayment> findByEmployeeIdAndMonthAndYear(Long employeeId, Integer month, Integer year);
}
