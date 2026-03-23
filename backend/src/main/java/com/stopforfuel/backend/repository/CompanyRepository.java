package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Company;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyRepository extends ScidRepository<Company> {
    List<Company> findByScid(Long scid);
}
