package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.CompanyDocument;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyDocumentRepository extends ScidRepository<CompanyDocument> {
    List<CompanyDocument> findByCompanyIdAndScid(Long companyId, Long scid);
}
