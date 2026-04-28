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

    // Eager-load shift (+ its attendant + role) for list endpoints so we don't
    // do 1 + N selects when serializing each report's nested shift summary.
    @EntityGraph(attributePaths = {"shift", "shift.attendant", "shift.attendant.role"})
    List<ShiftClosingReport> findByStatusAndScidOrderByReportDateDesc(String status, Long scid);

    @EntityGraph(attributePaths = {"shift", "shift.attendant", "shift.attendant.role"})
    List<ShiftClosingReport> findAllByScidOrderByReportDateDesc(Long scid);

    @Override
    @EntityGraph(attributePaths = {"shift", "shift.attendant", "shift.attendant.role"})
    List<ShiftClosingReport> findAllByScid(Long scid);
}
