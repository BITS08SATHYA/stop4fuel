package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.UpiCompany;
import com.stopforfuel.backend.repository.UpiCompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/upi-companies")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UpiCompanyController {

    private final UpiCompanyRepository repository;

    @GetMapping
    public List<UpiCompany> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public UpiCompany create(@RequestBody UpiCompany upiCompany) {
        return repository.save(upiCompany);
    }
}
