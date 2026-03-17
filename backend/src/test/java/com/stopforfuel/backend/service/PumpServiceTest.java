package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.repository.PumpRepository;
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
class PumpServiceTest {

    @Mock
    private PumpRepository pumpRepository;

    @InjectMocks
    private PumpService pumpService;

    private Pump testPump;

    @BeforeEach
    void setUp() {
        testPump = new Pump();
        testPump.setId(1L);
        testPump.setName("Pump 1");
        testPump.setActive(true);
        testPump.setScid(1L);
    }

    @Test
    void getAllPumps_returnsList() {
        when(pumpRepository.findAll()).thenReturn(List.of(testPump));
        assertEquals(1, pumpService.getAllPumps().size());
    }

    @Test
    void getActivePumps_returnsOnlyActive() {
        when(pumpRepository.findByActive(true)).thenReturn(List.of(testPump));
        assertEquals(1, pumpService.getActivePumps().size());
    }

    @Test
    void getPumpById_exists_returnsPump() {
        when(pumpRepository.findById(1L)).thenReturn(Optional.of(testPump));
        assertEquals("Pump 1", pumpService.getPumpById(1L).getName());
    }

    @Test
    void getPumpById_notExists_throwsException() {
        when(pumpRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> pumpService.getPumpById(99L));
    }

    @Test
    void createPump_withoutScid_defaultsTo1() {
        Pump pump = new Pump();
        pump.setName("New Pump");
        when(pumpRepository.save(any(Pump.class))).thenAnswer(i -> i.getArgument(0));

        Pump result = pumpService.createPump(pump);
        assertEquals(1L, result.getScid());
    }

    @Test
    void createPump_withScid_preservesIt() {
        testPump.setScid(2L);
        when(pumpRepository.save(any(Pump.class))).thenAnswer(i -> i.getArgument(0));

        Pump result = pumpService.createPump(testPump);
        assertEquals(2L, result.getScid());
    }

    @Test
    void updatePump_updatesName() {
        Pump details = new Pump();
        details.setName("Updated Pump");

        when(pumpRepository.findById(1L)).thenReturn(Optional.of(testPump));
        when(pumpRepository.save(any(Pump.class))).thenAnswer(i -> i.getArgument(0));

        Pump result = pumpService.updatePump(1L, details);
        assertEquals("Updated Pump", result.getName());
    }

    @Test
    void toggleStatus_activeToInactive() {
        testPump.setActive(true);
        when(pumpRepository.findById(1L)).thenReturn(Optional.of(testPump));
        when(pumpRepository.save(any(Pump.class))).thenAnswer(i -> i.getArgument(0));

        assertFalse(pumpService.toggleStatus(1L).isActive());
    }

    @Test
    void deletePump_callsRepository() {
        pumpService.deletePump(1L);
        verify(pumpRepository).deleteById(1L);
    }
}
