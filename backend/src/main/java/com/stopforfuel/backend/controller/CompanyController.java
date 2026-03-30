package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.CompanyDTO;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.service.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<CompanyDTO> getAllCompanies() {
        return companyService.getAllCompanies().stream().map(CompanyDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public CompanyDTO getCompanyById(@PathVariable Long id) {
        return companyService.getCompanyById(id).map(CompanyDTO::from).orElse(null);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_MANAGE')")
    public CompanyDTO createCompany(@Valid @RequestBody Company company) {
        return CompanyDTO.from(companyService.saveCompany(company));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_MANAGE')")
    public CompanyDTO updateCompany(@PathVariable Long id, @Valid @RequestBody Company company) {
        company.setId(id);
        return CompanyDTO.from(companyService.saveCompany(company));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_MANAGE')")
    public void deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
    }
}
