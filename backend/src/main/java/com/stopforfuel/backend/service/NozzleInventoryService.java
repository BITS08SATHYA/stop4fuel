package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.repository.NozzleInventoryRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NozzleInventoryService {

    private final NozzleInventoryRepository repository;
    private final ShiftService shiftService;

    public List<NozzleInventory> getAll() {
        return repository.findAllByScidWithNozzle(SecurityUtils.getScid());
    }

    public List<NozzleInventory> getByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public List<NozzleInventory> getByNozzleId(Long nozzleId) {
        return repository.findByNozzleId(nozzleId);
    }

    public NozzleInventory getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("NozzleInventory not found with id: " + id));
    }

    public NozzleInventory save(NozzleInventory inventory) {
        if (inventory.getScid() == null) {
            inventory.setScid(SecurityUtils.getScid());
        }
        if (inventory.getShiftId() == null) {
            Shift activeShift = shiftService.getActiveShift();
            if (activeShift != null) {
                inventory.setShiftId(activeShift.getId());
            }
        }
        calculateFields(inventory);
        return repository.save(inventory);
    }

    public NozzleInventory update(Long id, NozzleInventory details) {
        NozzleInventory existing = getById(id);
        existing.setDate(details.getDate());
        existing.setNozzle(details.getNozzle());
        existing.setOpenMeterReading(details.getOpenMeterReading());
        existing.setCloseMeterReading(details.getCloseMeterReading());
        calculateFields(existing);
        return repository.save(existing);
    }

    public List<NozzleInventory> getByDateRange(LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndDateBetween(SecurityUtils.getScid(), fromDate, toDate);
    }

    public List<NozzleInventory> getByNozzleAndDateRange(Long nozzleId, LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndNozzleIdAndDateBetween(SecurityUtils.getScid(), nozzleId, fromDate, toDate);
    }

    public List<NozzleInventory> getByProductAndDateRange(Long productId, LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndProductIdAndDateBetween(SecurityUtils.getScid(), productId, fromDate, toDate);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private void calculateFields(NozzleInventory inventory) {
        if (inventory.getOpenMeterReading() != null && inventory.getCloseMeterReading() != null) {
            inventory.setSales(inventory.getCloseMeterReading() - inventory.getOpenMeterReading());
        }
    }
}
