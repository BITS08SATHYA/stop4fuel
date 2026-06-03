package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.DipChartPoint;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DipChartPointRepository extends ScidRepository<DipChartPoint> {

    List<DipChartPoint> findByChartIdOrderByDipMmAsc(Long chartId);

    long countByChartId(Long chartId);
}
