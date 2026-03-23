package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    public List<Company> getAllCompanies() {
        return companyRepository.findAllByScid(SecurityUtils.getScid());
    }

    public List<Company> getCompaniesByScid(Long scid) {
        return companyRepository.findByScid(scid);
    }

    public Optional<Company> getCompanyById(Long id) {
        return companyRepository.findByIdAndScid(id, SecurityUtils.getScid());
    }

    public Company saveCompany(Company company) {
        return companyRepository.save(company);
    }

    public void deleteCompany(Long id) {
        companyRepository.deleteById(id);
    }
}
