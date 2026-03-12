package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository repository;

    public List<Shift> getAllShifts() {
        return repository.findAll();
    }

    public Shift openShift(Shift shift) {
        // Ensure no other shift is open (basic logic for now)
        repository.findByStatus("OPEN").ifPresent(s -> {
            throw new RuntimeException("A shift is already open. Close it before opening a new one.");
        });

        shift.setStartTime(LocalDateTime.now());
        shift.setStatus("OPEN");
        if (shift.getScid() == null) {
            shift.setScid(1L);
        }
        return repository.save(shift);
    }

    public Shift closeShift(Long id) {
        Shift shift = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shift not found"));
        
        shift.setEndTime(LocalDateTime.now());
        shift.setStatus("CLOSED");
        return repository.save(shift);
    }

    public Shift getActiveShift() {
        return repository.findByStatus("OPEN").orElse(null);
    }
}
