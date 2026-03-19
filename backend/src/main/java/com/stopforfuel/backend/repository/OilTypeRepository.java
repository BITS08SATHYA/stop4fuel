package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.OilType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OilTypeRepository extends JpaRepository<OilType, Long> {
    List<OilType> findByActiveTrue();
    Optional<OilType> findByName(String name);
}
