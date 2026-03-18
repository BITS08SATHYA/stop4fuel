package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ExternalCashInflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExternalCashInflowRepository extends JpaRepository<ExternalCashInflow, Long> {
    List<ExternalCashInflow> findByShiftIdOrderByInflowDateDesc(Long shiftId);
    List<ExternalCashInflow> findByStatusOrderByInflowDateDesc(String status);
    List<ExternalCashInflow> findAllByOrderByInflowDateDesc();
}
