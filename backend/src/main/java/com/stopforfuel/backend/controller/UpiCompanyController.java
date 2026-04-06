package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.UpiCompanyDTO;
import com.stopforfuel.backend.entity.UpiCompany;
import com.stopforfuel.backend.repository.UpiCompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/upi-companies")
@RequiredArgsConstructor
public class UpiCompanyController {

    private final UpiCompanyRepository repository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<UpiCompany> getAll() {
        return repository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_CREATE')")
    public UpiCompany create(@Valid @RequestBody UpiCompany upiCompany) {
        return repository.save(upiCompany);
    }
}
