package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.GradeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeTypeRepository extends ScidRepository<GradeType> {
    List<GradeType> findByActiveTrue();
    List<GradeType> findByOilTypeIdAndActiveTrue(Long oilTypeId);
    Optional<GradeType> findByName(String name);
    List<GradeType> findByActiveTrueAndScid(Long scid);
    List<GradeType> findByOilTypeIdAndActiveTrueAndScid(Long oilTypeId, Long scid);
}
