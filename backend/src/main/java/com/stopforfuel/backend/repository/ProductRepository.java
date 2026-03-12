package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryIgnoreCase(String category);
    List<Product> findByActive(boolean active);
    List<Product> findByCategoryIgnoreCaseAndActive(String category, boolean active);
}

