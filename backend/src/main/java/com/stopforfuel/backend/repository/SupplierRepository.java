package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Supplier;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupplierRepository extends ScidRepository<Supplier> {
    List<Supplier> findByActiveTrue();
    List<Supplier> findByActiveTrueAndScid(Long scid);
}
