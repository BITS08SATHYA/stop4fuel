package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.CompanyDTO;
import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.backend.service.CompanyService;
import com.stopforfuel.backend.service.S3StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3StorageService s3StorageService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<CompanyDTO> getAllCompanies() {
        return companyService.getAllCompanies().stream().map(CompanyDTO::from).toList();
    }

    @GetMapping("/print-info")
    public CompanyDTO getCompanyPrintInfo() {
        return companyService.getAllCompanies().stream().findFirst().map(CompanyDTO::from).orElse(null);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public CompanyDTO getCompanyById(@PathVariable Long id) {
        return companyService.getCompanyById(id).map(CompanyDTO::from).orElse(null);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_CREATE')")
    public CompanyDTO createCompany(@Valid @RequestBody Company company,
                                     @RequestParam(required = false) Long ownerId) {
        if (ownerId != null) {
            userRepository.findById(ownerId).ifPresent(company::setOwner);
        }
        return CompanyDTO.from(companyService.saveCompany(company));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_UPDATE')")
    public CompanyDTO updateCompany(@PathVariable Long id, @Valid @RequestBody Company company,
                                     @RequestParam(required = false) Long ownerId) {
        company.setId(id);
        if (ownerId != null) {
            userRepository.findById(ownerId).ifPresent(company::setOwner);
        }
        return CompanyDTO.from(companyService.saveCompany(company));
    }

    @PostMapping("/{id}/logo")
    @PreAuthorize("hasPermission(null, 'SETTINGS_UPDATE')")
    public ResponseEntity<CompanyDTO> uploadLogo(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile file) {
        com.stopforfuel.backend.util.FileUploadValidator.validateImage(file);

        Company company = companyService.getCompanyById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        String key = String.format("companies/%d/logo/%s", id, file.getOriginalFilename());

        // Delete old logo if exists
        if (company.getLogoUrl() != null && !company.getLogoUrl().isEmpty()) {
            try { s3StorageService.delete(company.getLogoUrl()); } catch (Exception ignored) {}
        }

        try {
            s3StorageService.upload(key, file.getBytes(), file.getContentType());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload logo: " + e.getMessage());
        }

        company.setLogoUrl(key);
        return ResponseEntity.ok(CompanyDTO.from(companyService.saveCompany(company)));
    }

    @GetMapping("/{id}/logo-url")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public ResponseEntity<Map<String, String>> getLogoUrl(@PathVariable Long id) {
        Company company = companyService.getCompanyById(id)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        if (company.getLogoUrl() == null || company.getLogoUrl().isEmpty()) {
            return ResponseEntity.ok(Map.of("url", ""));
        }
        return ResponseEntity.ok(Map.of("url", s3StorageService.getPresignedUrl(company.getLogoUrl())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_DELETE')")
    public void deleteCompany(@PathVariable Long id) {
        companyService.deleteCompany(id);
    }
}
