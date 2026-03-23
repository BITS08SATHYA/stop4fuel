package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.repository.GradeTypeRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GradeTypeService {

    private final GradeTypeRepository repository;

    public List<GradeType> getAllGrades() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public List<GradeType> getActiveGrades() {
        return repository.findByActiveTrueAndScid(SecurityUtils.getScid());
    }

    public List<GradeType> getActiveGradesByOilType(Long oilTypeId) {
        return repository.findByOilTypeIdAndActiveTrueAndScid(oilTypeId, SecurityUtils.getScid());
    }

    public GradeType getGradeById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("GradeType not found with id: " + id));
    }

    public GradeType createGrade(GradeType grade) {
        if (grade.getScid() == null) {
            grade.setScid(SecurityUtils.getScid());
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
        GradeType grade = getGradeById(id);
        repository.delete(grade);
    }
}
