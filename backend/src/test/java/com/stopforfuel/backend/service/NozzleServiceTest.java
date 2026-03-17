package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.backend.repository.PumpRepository;
import com.stopforfuel.backend.repository.TankRepository;
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
class NozzleServiceTest {

    @Mock
    private NozzleRepository nozzleRepository;

    @Mock
    private TankRepository tankRepository;

    @Mock
    private PumpRepository pumpRepository;

    @InjectMocks
    private NozzleService nozzleService;

    private Nozzle testNozzle;
    private Tank testTank;
    private Pump testPump;

    @BeforeEach
    void setUp() {
        testTank = new Tank();
        testTank.setId(1L);
        testTank.setName("Tank A");

        testPump = new Pump();
        testPump.setId(1L);
        testPump.setName("Pump 1");

        testNozzle = new Nozzle();
        testNozzle.setId(1L);
        testNozzle.setNozzleName("Nozzle 1");
        testNozzle.setNozzleNumber("N001");
        testNozzle.setTank(testTank);
        testNozzle.setPump(testPump);
        testNozzle.setActive(true);
        testNozzle.setScid(1L);
    }

    @Test
    void getAllNozzles_returnsList() {
        when(nozzleRepository.findAll()).thenReturn(List.of(testNozzle));
        assertEquals(1, nozzleService.getAllNozzles().size());
    }

    @Test
    void getActiveNozzles_returnsOnlyActive() {
        when(nozzleRepository.findByActive(true)).thenReturn(List.of(testNozzle));
        assertEquals(1, nozzleService.getActiveNozzles().size());
    }

    @Test
    void getNozzlesByTank_returnsList() {
        when(nozzleRepository.findByTankId(1L)).thenReturn(List.of(testNozzle));
        assertEquals(1, nozzleService.getNozzlesByTank(1L).size());
    }

    @Test
    void getNozzlesByPump_returnsList() {
        when(nozzleRepository.findByPumpId(1L)).thenReturn(List.of(testNozzle));
        assertEquals(1, nozzleService.getNozzlesByPump(1L).size());
    }

    @Test
    void getNozzleById_exists_returnsNozzle() {
        when(nozzleRepository.findById(1L)).thenReturn(Optional.of(testNozzle));
        assertEquals("Nozzle 1", nozzleService.getNozzleById(1L).getNozzleName());
    }

    @Test
    void getNozzleById_notExists_throwsException() {
        when(nozzleRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> nozzleService.getNozzleById(99L));
    }

    @Test
    void createNozzle_validatesAndSetsRelations() {
        when(tankRepository.findById(1L)).thenReturn(Optional.of(testTank));
        when(pumpRepository.findById(1L)).thenReturn(Optional.of(testPump));
        when(nozzleRepository.save(any(Nozzle.class))).thenAnswer(i -> i.getArgument(0));

        Nozzle result = nozzleService.createNozzle(testNozzle);
        assertEquals(testTank, result.getTank());
        assertEquals(testPump, result.getPump());
    }

    @Test
    void createNozzle_withoutScid_defaultsTo1() {
        Nozzle nozzle = new Nozzle();
        nozzle.setNozzleName("New Nozzle");
        when(nozzleRepository.save(any(Nozzle.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1L, nozzleService.createNozzle(nozzle).getScid());
    }

    @Test
    void createNozzle_tankNotFound_throwsException() {
        Tank missingTank = new Tank();
        missingTank.setId(99L);
        testNozzle.setTank(missingTank);
        when(tankRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> nozzleService.createNozzle(testNozzle));
    }

    @Test
    void updateNozzle_updatesFields() {
        Nozzle details = new Nozzle();
        details.setNozzleName("Updated Nozzle");
        details.setNozzleNumber("N002");
        details.setNozzleCompany("HP");
        details.setTank(testTank);
        details.setPump(testPump);

        when(nozzleRepository.findById(1L)).thenReturn(Optional.of(testNozzle));
        when(tankRepository.findById(1L)).thenReturn(Optional.of(testTank));
        when(pumpRepository.findById(1L)).thenReturn(Optional.of(testPump));
        when(nozzleRepository.save(any(Nozzle.class))).thenAnswer(i -> i.getArgument(0));

        Nozzle result = nozzleService.updateNozzle(1L, details);
        assertEquals("Updated Nozzle", result.getNozzleName());
        assertEquals("N002", result.getNozzleNumber());
    }

    @Test
    void toggleStatus_activeToInactive() {
        testNozzle.setActive(true);
        when(nozzleRepository.findById(1L)).thenReturn(Optional.of(testNozzle));
        when(nozzleRepository.save(any(Nozzle.class))).thenAnswer(i -> i.getArgument(0));

        assertFalse(nozzleService.toggleStatus(1L).isActive());
    }

    @Test
    void deleteNozzle_callsRepository() {
        nozzleService.deleteNozzle(1L);
        verify(nozzleRepository).deleteById(1L);
    }
}
