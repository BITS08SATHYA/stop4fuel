package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CreditPolicy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CreditPolicyRepository extends ScidRepository<CreditPolicy> {

    Optional<CreditPolicy> findByCustomerCategoryIdAndScid(Long categoryId, Long scid);

    @Query("SELECT cp FROM CreditPolicy cp WHERE cp.customerCategory IS NULL AND cp.scid = :scid")
    Optional<CreditPolicy> findDefaultByScid(@Param("scid") Long scid);
}
