package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ReportLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportLineItemRepository extends JpaRepository<ReportLineItem, Long> {
    List<ReportLineItem> findByReportIdOrderBySortOrder(Long reportId);
    List<ReportLineItem> findByReportIdAndSectionOrderBySortOrder(Long reportId, String section);
    void deleteByReportId(Long reportId);
}
