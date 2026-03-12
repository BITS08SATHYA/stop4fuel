package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.GradeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeTypeRepository extends JpaRepository<GradeType, Long> {
    List<GradeType> findByActiveTrue();
    Optional<GradeType> findByName(String name);
}
