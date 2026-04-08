package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ShiftClosingReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftClosingReportRepository extends ScidRepository<ShiftClosingReport> {
    @EntityGraph(attributePaths = {"lineItems", "shift"})
    Optional<ShiftClosingReport> findByShift_Id(Long shiftId);
    List<ShiftClosingReport> findByStatusAndScidOrderByReportDateDesc(String status, Long scid);
    List<ShiftClosingReport> findAllByScidOrderByReportDateDesc(Long scid);
}
