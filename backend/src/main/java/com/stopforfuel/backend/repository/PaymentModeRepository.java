package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.PaymentMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentModeRepository extends JpaRepository<PaymentMode, Long> {
    Optional<PaymentMode> findByModeName(String modeName);
}
