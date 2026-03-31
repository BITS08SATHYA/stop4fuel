package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.TankInventoryRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TankInventoryService {

    private final TankInventoryRepository repository;
    private final ShiftService shiftService;

    @Transactional(readOnly = true)
    public List<TankInventory> getAll() {
        return repository.findAllByScidWithTank(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<TankInventory> getByDate(LocalDate date) {
        return repository.findByDateWithTank(date);
    }

    @Transactional(readOnly = true)
    public List<TankInventory> getByTankId(Long tankId) {
        return repository.findByTankIdWithTank(tankId);
    }

    @Transactional(readOnly = true)
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
            if (activeShift == null) {
                throw new BusinessException("No active shift. Please open a shift before saving tank inventory.");
            }
            inventory.setShiftId(activeShift.getId());
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

    @Transactional(readOnly = true)
    public List<TankInventory> getByDateRange(LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndDateBetween(SecurityUtils.getScid(), fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public List<TankInventory> getByTankAndDateRange(Long tankId, LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndTankIdAndDateBetween(SecurityUtils.getScid(), tankId, fromDate, toDate);
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
