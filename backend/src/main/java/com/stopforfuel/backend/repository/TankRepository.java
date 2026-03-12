package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Tank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TankRepository extends JpaRepository<Tank, Long> {
    List<Tank> findByProductId(Long productId);
    List<Tank> findByActive(boolean active);
}
