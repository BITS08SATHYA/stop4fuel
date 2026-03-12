package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.UpiCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UpiCompanyRepository extends JpaRepository<UpiCompany, Long> {
}
