package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ShiftClosingReport;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftClosingReportRepository extends ScidRepository<ShiftClosingReport> {
    Optional<ShiftClosingReport> findByShiftId(Long shiftId);
    List<ShiftClosingReport> findByStatusOrderByReportDateDesc(String status);
    List<ShiftClosingReport> findAllByOrderByReportDateDesc();
}
