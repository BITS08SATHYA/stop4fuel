package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ExternalCashInflow;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExternalCashInflowRepository extends ScidRepository<ExternalCashInflow> {
    List<ExternalCashInflow> findByShiftIdOrderByInflowDateDesc(Long shiftId);
    List<ExternalCashInflow> findByStatusOrderByInflowDateDesc(String status);
    List<ExternalCashInflow> findAllByOrderByInflowDateDesc();
}
