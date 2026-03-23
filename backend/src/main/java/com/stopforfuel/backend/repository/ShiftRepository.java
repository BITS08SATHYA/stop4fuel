package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Shift;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShiftRepository extends ScidRepository<Shift> {
    Optional<Shift> findByStatus(String status);
    Optional<Shift> findByStatusAndScid(String status, Long scid);
}
