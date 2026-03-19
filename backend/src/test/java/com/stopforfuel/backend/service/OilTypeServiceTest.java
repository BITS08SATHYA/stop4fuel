package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.OilType;
import com.stopforfuel.backend.repository.OilTypeRepository;
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
class OilTypeServiceTest {

    @Mock
    private OilTypeRepository repository;

    @InjectMocks
    private OilTypeService oilTypeService;

    private OilType testOilType;

    @BeforeEach
    void setUp() {
        testOilType = new OilType();
        testOilType.setId(1L);
        testOilType.setName("Engine Oil");
        testOilType.setDescription("Standard engine oil");
        testOilType.setActive(true);
    }

    @Test
    void getAllOilTypes_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testOilType));

        List<OilType> result = oilTypeService.getAllOilTypes();

        assertEquals(1, result.size());
        assertEquals("Engine Oil", result.get(0).getName());
        verify(repository).findAll();
    }

    @Test
    void getActiveOilTypes_returnsActiveOnly() {
        when(repository.findByActiveTrue()).thenReturn(List.of(testOilType));

        List<OilType> result = oilTypeService.getActiveOilTypes();

        assertEquals(1, result.size());
        assertTrue(result.get(0).isActive());
        verify(repository).findByActiveTrue();
    }

    @Test
    void getOilTypeById_exists_returnsOilType() {
        when(repository.findById(1L)).thenReturn(Optional.of(testOilType));

        OilType result = oilTypeService.getOilTypeById(1L);

        assertEquals("Engine Oil", result.getName());
    }

    @Test
    void getOilTypeById_notFound_throws() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> oilTypeService.getOilTypeById(99L));
        assertTrue(ex.getMessage().contains("OilType not found"));
    }

    @Test
    void createOilType_saves() {
        when(repository.save(any(OilType.class))).thenAnswer(i -> i.getArgument(0));

        OilType result = oilTypeService.createOilType(testOilType);

        assertEquals("Engine Oil", result.getName());
        verify(repository).save(testOilType);
    }

    @Test
    void updateOilType_updatesFields() {
        OilType details = new OilType();
        details.setName("Updated Oil");
        details.setDescription("Updated description");
        details.setActive(false);

        when(repository.findById(1L)).thenReturn(Optional.of(testOilType));
        when(repository.save(any(OilType.class))).thenAnswer(i -> i.getArgument(0));

        OilType result = oilTypeService.updateOilType(1L, details);

        assertEquals("Updated Oil", result.getName());
        assertEquals("Updated description", result.getDescription());
        assertFalse(result.isActive());
    }

    @Test
    void toggleStatus_flipsActive() {
        testOilType.setActive(true);
        when(repository.findById(1L)).thenReturn(Optional.of(testOilType));
        when(repository.save(any(OilType.class))).thenAnswer(i -> i.getArgument(0));

        OilType result = oilTypeService.toggleStatus(1L);

        assertFalse(result.isActive());
    }

    @Test
    void deleteOilType_callsDeleteById() {
        oilTypeService.deleteOilType(1L);

        verify(repository).deleteById(1L);
    }
}
