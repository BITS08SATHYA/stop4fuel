package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ExternalCashInflow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalCashInflowRepository extends ScidRepository<ExternalCashInflow> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ExternalCashInflow e WHERE e.id = :id AND e.scid = :scid")
    Optional<ExternalCashInflow> findByIdAndScidForUpdate(@Param("id") Long id, @Param("scid") Long scid);
    List<ExternalCashInflow> findByShiftIdOrderByInflowDateDesc(Long shiftId);
    List<ExternalCashInflow> findByStatusOrderByInflowDateDesc(String status);
    List<ExternalCashInflow> findAllByOrderByInflowDateDesc();
}
