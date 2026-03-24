package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Product;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends ScidRepository<Product> {
    List<Product> findByCategoryIgnoreCase(String category);
    List<Product> findByActive(boolean active);
    List<Product> findByCategoryIgnoreCaseAndActive(String category, boolean active);
    List<Product> findByActiveAndScid(boolean active, Long scid);
    List<Product> findByCategoryIgnoreCaseAndActiveAndScid(String category, boolean active, Long scid);
    List<Product> findByCategoryNotIgnoreCaseAndActiveAndScid(String category, boolean active, Long scid);
}

