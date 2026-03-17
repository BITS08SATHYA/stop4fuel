package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.NozzleInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NozzleInventoryServiceTest {

    @Mock
    private NozzleInventoryRepository repository;

    @Mock
    private ShiftService shiftService;

    @InjectMocks
    private NozzleInventoryService nozzleInventoryService;

    private NozzleInventory testInventory;

    @BeforeEach
    void setUp() {
        testInventory = new NozzleInventory();
        testInventory.setId(1L);
        testInventory.setDate(LocalDate.now());
        testInventory.setOpenMeterReading(10000.0);
        testInventory.setCloseMeterReading(10500.0);
        testInventory.setScid(1L);
    }

    @Test
    void getAll_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testInventory));
        assertEquals(1, nozzleInventoryService.getAll().size());
    }

    @Test
    void getByDate_returnsList() {
        LocalDate today = LocalDate.now();
        when(repository.findByDate(today)).thenReturn(List.of(testInventory));
        assertEquals(1, nozzleInventoryService.getByDate(today).size());
    }

    @Test
    void getByNozzleId_returnsList() {
        when(repository.findByNozzleId(1L)).thenReturn(List.of(testInventory));
        assertEquals(1, nozzleInventoryService.getByNozzleId(1L).size());
    }

    @Test
    void getById_exists_returnsInventory() {
        when(repository.findById(1L)).thenReturn(Optional.of(testInventory));
        assertNotNull(nozzleInventoryService.getById(1L));
    }

    @Test
    void getById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> nozzleInventoryService.getById(99L));
    }

    @Test
    void save_calculatesSales() {
        when(repository.save(any(NozzleInventory.class))).thenAnswer(i -> i.getArgument(0));

        NozzleInventory result = nozzleInventoryService.save(testInventory);
        assertEquals(500.0, result.getSales()); // 10500 - 10000
    }

    @Test
    void save_withoutScid_defaultsTo1() {
        NozzleInventory inv = new NozzleInventory();
        inv.setOpenMeterReading(100.0);
        inv.setCloseMeterReading(200.0);
        when(repository.save(any(NozzleInventory.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1L, nozzleInventoryService.save(inv).getScid());
    }

    @Test
    void save_assignsActiveShift() {
        testInventory.setShiftId(null);
        Shift shift = new Shift();
        shift.setId(3L);
        when(shiftService.getActiveShift()).thenReturn(shift);
        when(repository.save(any(NozzleInventory.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(3L, nozzleInventoryService.save(testInventory).getShiftId());
    }

    @Test
    void save_nullReadings_doesNotCalculateSales() {
        NozzleInventory inv = new NozzleInventory();
        inv.setScid(1L);
        when(repository.save(any(NozzleInventory.class))).thenAnswer(i -> i.getArgument(0));

        NozzleInventory result = nozzleInventoryService.save(inv);
        assertNull(result.getSales());
    }

    @Test
    void update_updatesFieldsAndRecalculates() {
        NozzleInventory details = new NozzleInventory();
        details.setDate(LocalDate.now());
        details.setOpenMeterReading(20000.0);
        details.setCloseMeterReading(20800.0);

        when(repository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(repository.save(any(NozzleInventory.class))).thenAnswer(i -> i.getArgument(0));

        NozzleInventory result = nozzleInventoryService.update(1L, details);
        assertEquals(800.0, result.getSales());
    }

    @Test
    void delete_callsRepository() {
        nozzleInventoryService.delete(1L);
        verify(repository).deleteById(1L);
    }
}
