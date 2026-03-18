package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.EmployeeAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeAdvanceRepository extends JpaRepository<EmployeeAdvance, Long> {
    List<EmployeeAdvance> findByEmployeeIdOrderByAdvanceDateDesc(Long employeeId);
    List<EmployeeAdvance> findByEmployeeIdAndStatus(Long employeeId, String status);
    List<EmployeeAdvance> findByAdvanceDateBetween(java.time.LocalDate from, java.time.LocalDate to);
}
