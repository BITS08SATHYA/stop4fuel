package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ProductPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPriceHistoryRepository extends JpaRepository<ProductPriceHistory, Long> {

    List<ProductPriceHistory> findByProductIdOrderByEffectiveDateDesc(Long productId);

    List<ProductPriceHistory> findByEffectiveDateBetweenOrderByEffectiveDateDesc(LocalDate from, LocalDate to);

    List<ProductPriceHistory> findByProductIdAndEffectiveDateBetweenOrderByEffectiveDateDesc(
            Long productId, LocalDate from, LocalDate to);

    Optional<ProductPriceHistory> findTopByProductIdOrderByEffectiveDateDesc(Long productId);

    Optional<ProductPriceHistory> findByProductIdAndEffectiveDate(Long productId, LocalDate effectiveDate);
}
