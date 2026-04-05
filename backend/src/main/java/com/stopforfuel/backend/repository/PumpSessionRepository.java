package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PumpSession;
import com.stopforfuel.backend.enums.PumpSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PumpSessionRepository extends JpaRepository<PumpSession, Long> {

    Optional<PumpSession> findByAttendantIdAndStatusAndScid(Long attendantId, PumpSessionStatus status, Long scid);

    Optional<PumpSession> findByPumpIdAndStatusAndScid(Long pumpId, PumpSessionStatus status, Long scid);

    List<PumpSession> findByShiftIdAndScidOrderByStartTimeDesc(Long shiftId, Long scid);

    List<PumpSession> findByAttendantIdAndScidOrderByStartTimeDesc(Long attendantId, Long scid);
}
