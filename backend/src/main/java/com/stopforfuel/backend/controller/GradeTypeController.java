package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.GradeTypeDTO;
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
    public List<GradeTypeDTO> getAll() {
        return service.getAllGrades().stream().map(GradeTypeDTO::from).toList();
    }

    @GetMapping("/active")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<GradeTypeDTO> getActive() {
        return service.getActiveGrades().stream().map(GradeTypeDTO::from).toList();
    }

    @GetMapping("/oil-type/{oilTypeId}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public List<GradeTypeDTO> getByOilType(@PathVariable Long oilTypeId) {
        return service.getActiveGradesByOilType(oilTypeId).stream().map(GradeTypeDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_VIEW')")
    public GradeTypeDTO getById(@PathVariable Long id) {
        return GradeTypeDTO.from(service.getGradeById(id));
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'PRODUCT_CREATE')")
    public GradeTypeDTO create(@Valid @RequestBody GradeType grade) {
        return GradeTypeDTO.from(service.createGrade(grade));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public GradeTypeDTO update(@PathVariable Long id, @Valid @RequestBody GradeType grade) {
        return GradeTypeDTO.from(service.updateGrade(id, grade));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'PRODUCT_UPDATE')")
    public GradeTypeDTO toggleStatus(@PathVariable Long id) {
        return GradeTypeDTO.from(service.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'PRODUCT_DELETE')")
    public void delete(@PathVariable Long id) {
        service.deleteGrade(id);
    }
}
