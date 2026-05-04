package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.enums.ShiftStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRepository extends ScidRepository<Shift> {
    Optional<Shift> findByStatus(ShiftStatus status);
    Optional<Shift> findByStatusAndScid(ShiftStatus status, Long scid);
    long countByScidAndStatus(Long scid, ShiftStatus status);
    Optional<Shift> findTopByStatusAndScidOrderByIdDesc(ShiftStatus status, Long scid);
    Optional<Shift> findTopByStatusInAndScidOrderByIdDesc(List<ShiftStatus> statuses, Long scid);

    List<Shift> findByScidAndStartTimeBetweenOrderByStartTimeAsc(Long scid, LocalDateTime from, LocalDateTime to);

    // Eager-load attendant (+ its EAGER role) so the Shift History page doesn't
    // do 1 + 2N selects when mapping to ShiftDTO. Used by service.getAllShifts().
    @EntityGraph(attributePaths = {"attendant", "attendant.role"})
    List<Shift> findByScidOrderByIdDesc(Long scid);

    @EntityGraph(attributePaths = {"attendant", "attendant.role"})
    List<Shift> findByScidAndStatusInOrderByIdDesc(Long scid, List<ShiftStatus> statuses, Pageable pageable);

    /**
     * Shifts an admin can move an existing invoice into: status OPEN/REVIEW (no/DRAFT report yet),
     * or status CLOSED whose closing report is still DRAFT (Recompute is allowed). Excludes any
     * shift whose report is FINALIZED — un-finalize that report explicitly first.
     */
    @EntityGraph(attributePaths = {"attendant", "attendant.role"})
    @Query("""
            SELECT s FROM Shift s
            WHERE s.scid = :scid
              AND (s.status IN (com.stopforfuel.backend.enums.ShiftStatus.OPEN,
                                com.stopforfuel.backend.enums.ShiftStatus.REVIEW)
                   OR (s.status = com.stopforfuel.backend.enums.ShiftStatus.CLOSED
                       AND EXISTS (SELECT 1 FROM ShiftClosingReport r
                                   WHERE r.shift = s AND r.status = 'DRAFT')))
            ORDER BY s.id DESC
            """)
    List<Shift> findMovable(@Param("scid") Long scid, Pageable pageable);

    /**
     * Find the shift whose [start_time, end_time] window contains :timestamp. Used by orphan-bill
     * auto-fix to pick a covering shift by bill_date. Returns Optional.empty() if no shift covers
     * the timestamp (e.g. a created-at falling in the gap between two shifts — the original cause
     * of the 2026-04-03 NULL-shift bills). Active shifts have end_time IS NULL — for those the
     * upper bound is open-ended (NOW()).
     */
    @Query("""
            SELECT s FROM Shift s
            WHERE s.scid = :scid
              AND s.startTime <= :timestamp
              AND (s.endTime IS NULL OR s.endTime >= :timestamp)
            ORDER BY s.startTime DESC
            """)
    List<Shift> findCoveringShifts(
            @Param("scid") Long scid,
            @Param("timestamp") LocalDateTime timestamp,
            Pageable pageable);
}
