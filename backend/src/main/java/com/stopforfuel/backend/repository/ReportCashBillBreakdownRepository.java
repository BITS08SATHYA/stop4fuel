package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ReportCashBillBreakdown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportCashBillBreakdownRepository extends JpaRepository<ReportCashBillBreakdown, Long> {
    List<ReportCashBillBreakdown> findByReportId(Long reportId);
    void deleteByReportId(Long reportId);
}
