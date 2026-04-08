package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Incentive;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncentiveRepository extends ScidRepository<Incentive> {

    @Query("SELECT i FROM Incentive i JOIN FETCH i.customer JOIN FETCH i.product WHERE i.scid = :scid ORDER BY i.customer.name, i.product.name")
    List<Incentive> findAllWithCustomerAndProduct(Long scid);

    @Query("SELECT i FROM Incentive i JOIN FETCH i.product WHERE i.customer.id = :customerId AND i.scid = :scid")
    List<Incentive> findByCustomerId(@org.springframework.data.repository.query.Param("customerId") Long customerId, @org.springframework.data.repository.query.Param("scid") Long scid);

    Optional<Incentive> findByCustomerIdAndProductIdAndActiveTrueAndScid(Long customerId, Long productId, Long scid);

    List<Incentive> findByCustomerIdAndActiveTrueAndScid(Long customerId, Long scid);
}
