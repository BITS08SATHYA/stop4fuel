package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.repository.ShiftRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShiftService {

    private final ShiftRepository repository;
    private final ShiftClosingReportService shiftClosingReportService;
    private final ProductInventoryService productInventoryService;

    public ShiftService(ShiftRepository repository,
                        @Lazy ShiftClosingReportService shiftClosingReportService,
                        @Lazy ProductInventoryService productInventoryService) {
        this.repository = repository;
        this.shiftClosingReportService = shiftClosingReportService;
        this.productInventoryService = productInventoryService;
    }

    public List<Shift> getAllShifts() {
        return repository.findAll();
    }

    public Shift openShift(Shift shift) {
        repository.findByStatus("OPEN").ifPresent(s -> {
            throw new RuntimeException("A shift is already open. Close it before opening a new one.");
        });

        shift.setStartTime(LocalDateTime.now());
        shift.setStatus("OPEN");
        if (shift.getScid() == null) {
            shift.setScid(SecurityUtils.getScid());
        }
        Shift saved = repository.save(shift);

        // Auto-create ProductInventory records for all active products
        productInventoryService.autoCreateForShift(saved);

        return saved;
    }

    public Shift closeShift(Long id) {
        Shift shift = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shift not found"));

        shift.setEndTime(LocalDateTime.now());
        shift.setStatus("CLOSED");
        Shift closed = repository.save(shift);

        // Finalize rate/amount on ProductInventory records
        productInventoryService.finalizeForShift(closed.getId());

        // Auto-generate shift closing report
        shiftClosingReportService.generateReport(closed.getId());

        return closed;
    }

    public Shift getActiveShift() {
        return repository.findByStatus("OPEN").orElse(null);
    }
}
