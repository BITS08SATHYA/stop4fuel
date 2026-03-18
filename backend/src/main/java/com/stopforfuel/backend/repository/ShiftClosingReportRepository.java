package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ShiftClosingReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftClosingReportRepository extends JpaRepository<ShiftClosingReport, Long> {
    Optional<ShiftClosingReport> findByShiftId(Long shiftId);
    List<ShiftClosingReport> findByStatusOrderByReportDateDesc(String status);
    List<ShiftClosingReport> findAllByOrderByReportDateDesc();
}
