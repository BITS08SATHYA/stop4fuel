package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CustomerBlockEvent;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerBlockEventRepository extends ScidRepository<CustomerBlockEvent> {

    List<CustomerBlockEvent> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<CustomerBlockEvent> findTopByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
