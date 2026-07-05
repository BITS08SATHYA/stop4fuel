package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerContactRepository extends JpaRepository<CustomerContact, Long> {

    @Query("select cc from CustomerContact cc where cc.customer.id = :customerId and cc.scid = :scid order by cc.id")
    List<CustomerContact> findByCustomerId(@Param("customerId") Long customerId, @Param("scid") Long scid);

    Optional<CustomerContact> findByIdAndScid(Long id, Long scid);
}
