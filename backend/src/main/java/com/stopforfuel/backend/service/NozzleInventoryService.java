package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.NozzleInventory;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.NozzleInventoryRepository;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NozzleInventoryService {

    private final NozzleInventoryRepository repository;
    private final NozzleRepository nozzleRepository;
    private final ShiftService shiftService;

    @Transactional(readOnly = true)
    public List<NozzleInventory> getAll() {
        return repository.findAllByScidWithNozzle(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<NozzleInventory> getByDate(LocalDate date) {
        return repository.findByDate(date);
    }

    @Transactional(readOnly = true)
    public List<NozzleInventory> getByNozzleId(Long nozzleId) {
        return repository.findByNozzleId(nozzleId);
    }

    @Transactional(readOnly = true)
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
            if (activeShift == null) {
                throw new BusinessException("No active shift. Please open a shift before saving nozzle inventory.");
            }
            inventory.setShiftId(activeShift.getId());
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
        existing.setTestQuantity(details.getTestQuantity());
        calculateFields(existing);
        return repository.save(existing);
    }

    @Transactional(readOnly = true)
    public List<NozzleInventory> getByDateRange(LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndDateBetween(SecurityUtils.getScid(), fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public List<NozzleInventory> getByNozzleAndDateRange(Long nozzleId, LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndNozzleIdAndDateBetween(SecurityUtils.getScid(), nozzleId, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public List<NozzleInventory> getByProductAndDateRange(Long productId, LocalDate fromDate, LocalDate toDate) {
        return repository.findByScidAndProductIdAndDateBetween(SecurityUtils.getScid(), productId, fromDate, toDate);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private void calculateFields(NozzleInventory inventory) {
        if (inventory.getOpenMeterReading() != null && inventory.getCloseMeterReading() != null) {
            double sales = inventory.getCloseMeterReading() - inventory.getOpenMeterReading();
            inventory.setSales(sales);

            // Set rate from product price and calculate amount
            Double rate = getProductRate(inventory);
            if (rate != null) {
                inventory.setRate(rate);
                double testQty = inventory.getTestQuantity() != null ? inventory.getTestQuantity() : 0.0;
                double billableSales = sales - testQty;
                inventory.setAmount(billableSales * rate);
            }
        }
    }

    private Double getProductRate(NozzleInventory inventory) {
        try {
            if (inventory.getNozzle() != null && inventory.getNozzle().getId() != null) {
                Nozzle nozzle = nozzleRepository.findById(inventory.getNozzle().getId()).orElse(null);
                if (nozzle != null && nozzle.getTank() != null && nozzle.getTank().getProduct() != null
                        && nozzle.getTank().getProduct().getPrice() != null) {
                    return nozzle.getTank().getProduct().getPrice().doubleValue();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
