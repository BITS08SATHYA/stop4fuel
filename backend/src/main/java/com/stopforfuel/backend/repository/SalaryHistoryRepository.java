package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.SalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {
    List<SalaryHistory> findByEmployeeIdOrderByEffectiveDateDesc(Long employeeId);
}
