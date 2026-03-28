package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.SalaryPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryPaymentRepository extends ScidRepository<SalaryPayment> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT sp FROM SalaryPayment sp WHERE sp.id = :id AND sp.scid = :scid")
    Optional<SalaryPayment> findByIdAndScidForUpdate(@Param("id") Long id, @Param("scid") Long scid);
    @Query("SELECT sp FROM SalaryPayment sp JOIN FETCH sp.employee WHERE sp.month = :month AND sp.year = :year")
    List<SalaryPayment> findByMonthAndYear(@Param("month") Integer month, @Param("year") Integer year);
    List<SalaryPayment> findByShiftId(Long shiftId);
    List<SalaryPayment> findByEmployeeIdOrderByYearDescMonthDesc(Long employeeId);
    Optional<SalaryPayment> findByEmployeeIdAndMonthAndYear(Long employeeId, Integer month, Integer year);
}
