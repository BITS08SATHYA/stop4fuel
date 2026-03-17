package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.repository.GradeTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GradeTypeServiceTest {

    @Mock
    private GradeTypeRepository repository;

    @InjectMocks
    private GradeTypeService gradeTypeService;

    private GradeType testGrade;

    @BeforeEach
    void setUp() {
        testGrade = new GradeType();
        testGrade.setId(1L);
        testGrade.setName("Premium Diesel");
        testGrade.setOilType("DIESEL");
        testGrade.setActive(true);
        testGrade.setScid(1L);
    }

    @Test
    void getAllGrades_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testGrade));
        assertEquals(1, gradeTypeService.getAllGrades().size());
    }

    @Test
    void getActiveGrades_returnsActive() {
        when(repository.findByActiveTrue()).thenReturn(List.of(testGrade));
        assertEquals(1, gradeTypeService.getActiveGrades().size());
    }

    @Test
    void getGradeById_exists_returnsGrade() {
        when(repository.findById(1L)).thenReturn(Optional.of(testGrade));
        assertEquals("Premium Diesel", gradeTypeService.getGradeById(1L).getName());
    }

    @Test
    void getGradeById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> gradeTypeService.getGradeById(99L));
    }

    @Test
    void createGrade_withoutScid_defaultsTo1() {
        GradeType grade = new GradeType();
        grade.setName("New Grade");
        when(repository.save(any(GradeType.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1L, gradeTypeService.createGrade(grade).getScid());
    }

    @Test
    void updateGrade_updatesFields() {
        GradeType details = new GradeType();
        details.setName("Updated Grade");
        details.setOilType("PETROL");
        details.setDescription("Updated desc");
        details.setActive(false);

        when(repository.findById(1L)).thenReturn(Optional.of(testGrade));
        when(repository.save(any(GradeType.class))).thenAnswer(i -> i.getArgument(0));

        GradeType result = gradeTypeService.updateGrade(1L, details);
        assertEquals("Updated Grade", result.getName());
        assertEquals("PETROL", result.getOilType());
        assertFalse(result.isActive());
    }

    @Test
    void toggleStatus_activeToInactive() {
        testGrade.setActive(true);
        when(repository.findById(1L)).thenReturn(Optional.of(testGrade));
        when(repository.save(any(GradeType.class))).thenAnswer(i -> i.getArgument(0));

        assertFalse(gradeTypeService.toggleStatus(1L).isActive());
    }

    @Test
    void deleteGrade_callsRepository() {
        gradeTypeService.deleteGrade(1L);
        verify(repository).deleteById(1L);
    }
}
