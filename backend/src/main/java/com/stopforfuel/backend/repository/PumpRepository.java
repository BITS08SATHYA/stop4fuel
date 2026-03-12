package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Pump;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PumpRepository extends JpaRepository<Pump, Long> {
    List<Pump> findByActive(boolean active);
}
