package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.enums.ShiftStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
}
