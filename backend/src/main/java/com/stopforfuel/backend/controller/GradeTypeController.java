package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.service.GradeTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
public class GradeTypeController {

    private final GradeTypeService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<GradeType> getAll() {
        return service.getAllGrades();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<GradeType> getActive() {
        return service.getActiveGrades();
    }

    @GetMapping("/oil-type/{oilTypeId}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<GradeType> getByOilType(@PathVariable Long oilTypeId) {
        return service.getActiveGradesByOilType(oilTypeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public GradeType getById(@PathVariable Long id) {
        return service.getGradeById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public GradeType create(@Valid @RequestBody GradeType grade) {
        return service.createGrade(grade);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public GradeType update(@PathVariable Long id, @Valid @RequestBody GradeType grade) {
        return service.updateGrade(id, grade);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public GradeType toggleStatus(@PathVariable Long id) {
        return service.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.deleteGrade(id);
    }
}
