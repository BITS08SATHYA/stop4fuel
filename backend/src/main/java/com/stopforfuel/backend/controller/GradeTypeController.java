package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.service.GradeTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grades")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GradeTypeController {

    private final GradeTypeService service;

    @GetMapping
    public List<GradeType> getAll() {
        return service.getAllGrades();
    }

    @GetMapping("/active")
    public List<GradeType> getActive() {
        return service.getActiveGrades();
    }

    @GetMapping("/oil-type/{oilTypeId}")
    public List<GradeType> getByOilType(@PathVariable Long oilTypeId) {
        return service.getActiveGradesByOilType(oilTypeId);
    }

    @GetMapping("/{id}")
    public GradeType getById(@PathVariable Long id) {
        return service.getGradeById(id);
    }

    @PostMapping
    public GradeType create(@Valid @RequestBody GradeType grade) {
        return service.createGrade(grade);
    }

    @PutMapping("/{id}")
    public GradeType update(@PathVariable Long id, @Valid @RequestBody GradeType grade) {
        return service.updateGrade(id, grade);
    }

    @PatchMapping("/{id}/toggle-status")
    public GradeType toggleStatus(@PathVariable Long id) {
        return service.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.deleteGrade(id);
    }
}
