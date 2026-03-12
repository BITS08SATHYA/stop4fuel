package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Incentive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncentiveRepository extends JpaRepository<Incentive, Long> {

    List<Incentive> findByCustomerId(Long customerId);

    Optional<Incentive> findByCustomerIdAndProductIdAndActiveTrue(Long customerId, Long productId);

    List<Incentive> findByCustomerIdAndActiveTrue(Long customerId);
}
