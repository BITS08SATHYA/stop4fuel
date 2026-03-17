package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.repository.TankInventoryRepository;
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
class TankInventoryServiceTest {

    @Mock
    private TankInventoryRepository repository;

    @Mock
    private ShiftService shiftService;

    @InjectMocks
    private TankInventoryService tankInventoryService;

    private TankInventory testInventory;

    @BeforeEach
    void setUp() {
        testInventory = new TankInventory();
        testInventory.setId(1L);
        testInventory.setDate(LocalDate.now());
        testInventory.setOpenStock(1000.0);
        testInventory.setIncomeStock(500.0);
        testInventory.setCloseStock(1200.0);
        testInventory.setScid(1L);
    }

    @Test
    void getAll_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testInventory));
        assertEquals(1, tankInventoryService.getAll().size());
    }

    @Test
    void getByDate_returnsList() {
        LocalDate today = LocalDate.now();
        when(repository.findByDate(today)).thenReturn(List.of(testInventory));
        assertEquals(1, tankInventoryService.getByDate(today).size());
    }

    @Test
    void getByTankId_returnsList() {
        when(repository.findByTankId(1L)).thenReturn(List.of(testInventory));
        assertEquals(1, tankInventoryService.getByTankId(1L).size());
    }

    @Test
    void getById_exists_returnsInventory() {
        when(repository.findById(1L)).thenReturn(Optional.of(testInventory));
        assertNotNull(tankInventoryService.getById(1L));
    }

    @Test
    void getById_notExists_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> tankInventoryService.getById(99L));
    }

    @Test
    void save_calculatesTotalAndSaleStock() {
        when(repository.save(any(TankInventory.class))).thenAnswer(i -> i.getArgument(0));

        TankInventory result = tankInventoryService.save(testInventory);

        assertEquals(1500.0, result.getTotalStock()); // 1000 + 500
        assertEquals(300.0, result.getSaleStock());    // 1500 - 1200
    }

    @Test
    void save_withoutScid_defaultsTo1() {
        TankInventory inv = new TankInventory();
        inv.setOpenStock(100.0);
        inv.setIncomeStock(0.0);
        when(repository.save(any(TankInventory.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(1L, tankInventoryService.save(inv).getScid());
    }

    @Test
    void save_withoutShift_assignsActiveShift() {
        testInventory.setShiftId(null);
        Shift activeShift = new Shift();
        activeShift.setId(5L);
        when(shiftService.getActiveShift()).thenReturn(activeShift);
        when(repository.save(any(TankInventory.class))).thenAnswer(i -> i.getArgument(0));

        TankInventory result = tankInventoryService.save(testInventory);
        assertEquals(5L, result.getShiftId());
    }

    @Test
    void save_nullStocks_treatsAsZero() {
        TankInventory inv = new TankInventory();
        inv.setOpenStock(null);
        inv.setIncomeStock(null);
        inv.setScid(1L);
        when(repository.save(any(TankInventory.class))).thenAnswer(i -> i.getArgument(0));

        TankInventory result = tankInventoryService.save(inv);
        assertEquals(0.0, result.getTotalStock());
    }

    @Test
    void update_updatesFieldsAndRecalculates() {
        TankInventory details = new TankInventory();
        details.setDate(LocalDate.now());
        details.setOpenStock(2000.0);
        details.setIncomeStock(1000.0);
        details.setCloseStock(2500.0);

        when(repository.findById(1L)).thenReturn(Optional.of(testInventory));
        when(repository.save(any(TankInventory.class))).thenAnswer(i -> i.getArgument(0));

        TankInventory result = tankInventoryService.update(1L, details);
        assertEquals(3000.0, result.getTotalStock());
        assertEquals(500.0, result.getSaleStock());
    }

    @Test
    void delete_callsRepository() {
        tankInventoryService.delete(1L);
        verify(repository).deleteById(1L);
    }
}
