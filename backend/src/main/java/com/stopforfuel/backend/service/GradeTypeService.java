package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.repository.GradeTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeTypeService {

    private final GradeTypeRepository repository;

    public List<GradeType> getAllGrades() {
        return repository.findAll();
    }

    public List<GradeType> getActiveGrades() {
        return repository.findByActiveTrue();
    }

    public GradeType getGradeById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("GradeType not found with id: " + id));
    }

    public GradeType createGrade(GradeType grade) {
        if (grade.getScid() == null) {
            grade.setScid(1L);
        }
        return repository.save(grade);
    }

    public GradeType updateGrade(Long id, GradeType details) {
        GradeType grade = getGradeById(id);
        grade.setName(details.getName());
        grade.setOilType(details.getOilType());
        grade.setDescription(details.getDescription());
        grade.setActive(details.isActive());
        return repository.save(grade);
    }

    public GradeType toggleStatus(Long id) {
        GradeType grade = getGradeById(id);
        grade.setActive(!grade.isActive());
        return repository.save(grade);
    }

    public void deleteGrade(Long id) {
        repository.deleteById(id);
    }
}
