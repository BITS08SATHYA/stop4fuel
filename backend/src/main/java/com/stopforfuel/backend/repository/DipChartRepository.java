package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.DipChart;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DipChartRepository extends ScidRepository<DipChart> {

    Optional<DipChart> findByTankIdAndScidAndActiveTrue(Long tankId, Long scid);

    List<DipChart> findByTankIdAndScid(Long tankId, Long scid);
}
