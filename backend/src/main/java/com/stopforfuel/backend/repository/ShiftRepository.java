package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.enums.ShiftStatus;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShiftRepository extends ScidRepository<Shift> {
    Optional<Shift> findByStatus(ShiftStatus status);
    Optional<Shift> findByStatusAndScid(ShiftStatus status, Long scid);
    long countByScidAndStatus(Long scid, ShiftStatus status);
    Optional<Shift> findTopByStatusAndScidOrderByIdDesc(ShiftStatus status, Long scid);
}
