package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private ShiftRepository repository;

    @InjectMocks
    private ShiftService shiftService;

    private Shift testShift;

    @BeforeEach
    void setUp() {
        testShift = new Shift();
        testShift.setId(1L);
        testShift.setStatus("OPEN");
        testShift.setStartTime(LocalDateTime.now());
        testShift.setScid(1L);
    }

    @Test
    void getAllShifts_returnsList() {
        when(repository.findAll()).thenReturn(List.of(testShift));

        List<Shift> result = shiftService.getAllShifts();

        assertEquals(1, result.size());
    }

    @Test
    void openShift_noExistingOpen_createsShift() {
        Shift newShift = new Shift();
        when(repository.findByStatus("OPEN")).thenReturn(Optional.empty());
        when(repository.save(any(Shift.class))).thenAnswer(i -> {
            Shift s = i.getArgument(0);
            s.setId(2L);
            return s;
        });

        Shift result = shiftService.openShift(newShift);

        assertEquals("OPEN", result.getStatus());
        assertNotNull(result.getStartTime());
        assertEquals(1L, result.getScid());
    }

    @Test
    void openShift_existingOpenShift_throwsException() {
        when(repository.findByStatus("OPEN")).thenReturn(Optional.of(testShift));

        Shift newShift = new Shift();
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> shiftService.openShift(newShift));
        assertTrue(ex.getMessage().contains("already open"));
    }

    @Test
    void openShift_withExistingScid_preservesScid() {
        Shift newShift = new Shift();
        newShift.setScid(5L);
        when(repository.findByStatus("OPEN")).thenReturn(Optional.empty());
        when(repository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        Shift result = shiftService.openShift(newShift);

        assertEquals(5L, result.getScid());
    }

    @Test
    void closeShift_existingShift_closesIt() {
        when(repository.findById(1L)).thenReturn(Optional.of(testShift));
        when(repository.save(any(Shift.class))).thenAnswer(i -> i.getArgument(0));

        Shift result = shiftService.closeShift(1L);

        assertEquals("CLOSED", result.getStatus());
        assertNotNull(result.getEndTime());
    }

    @Test
    void closeShift_notFound_throwsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> shiftService.closeShift(99L));
        assertTrue(ex.getMessage().contains("Shift not found"));
    }

    @Test
    void getActiveShift_exists_returnsShift() {
        when(repository.findByStatus("OPEN")).thenReturn(Optional.of(testShift));

        Shift result = shiftService.getActiveShift();

        assertNotNull(result);
        assertEquals("OPEN", result.getStatus());
    }

    @Test
    void getActiveShift_noOpenShift_returnsNull() {
        when(repository.findByStatus("OPEN")).thenReturn(Optional.empty());

        Shift result = shiftService.getActiveShift();

        assertNull(result);
    }
}
