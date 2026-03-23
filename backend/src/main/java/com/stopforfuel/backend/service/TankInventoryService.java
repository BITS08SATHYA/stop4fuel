package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.repository.TankInventoryRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TankInventoryService {

    private final TankInventoryRepository repository;
    private final ShiftService shiftService;

    public List<TankInventory> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public List<TankInventory> getByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public List<TankInventory> getByTankId(Long tankId) {
        return repository.findByTankId(tankId);
    }

    public TankInventory getById(Long id) {
        return repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("TankInventory not found with id: " + id));
    }

    public TankInventory save(TankInventory inventory) {
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

    public TankInventory update(Long id, TankInventory details) {
        TankInventory existing = getById(id);
        existing.setDate(details.getDate());
        existing.setTank(details.getTank());
        existing.setOpenDip(details.getOpenDip());
        existing.setOpenStock(details.getOpenStock());
        existing.setIncomeStock(details.getIncomeStock());
        existing.setCloseDip(details.getCloseDip());
        existing.setCloseStock(details.getCloseStock());
        calculateFields(existing);
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private void calculateFields(TankInventory inventory) {
        double open = inventory.getOpenStock() != null ? inventory.getOpenStock() : 0.0;
        double income = inventory.getIncomeStock() != null ? inventory.getIncomeStock() : 0.0;
        double total = open + income;
        inventory.setTotalStock(total);

        if (inventory.getCloseStock() != null) {
            inventory.setSaleStock(total - inventory.getCloseStock());
        }
    }
}
