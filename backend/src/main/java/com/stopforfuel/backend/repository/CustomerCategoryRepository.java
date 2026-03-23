package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CustomerCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerCategoryRepository extends JpaRepository<CustomerCategory, Long> {
    Optional<CustomerCategory> findByCategoryName(String categoryName);
    List<CustomerCategory> findByCategoryType(String categoryType);
}
