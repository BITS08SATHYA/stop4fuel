package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.backend.repository.PumpRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NozzleService {

    @Autowired
    private NozzleRepository nozzleRepository;

    @Autowired
    private TankRepository tankRepository;

    @Autowired
    private PumpRepository pumpRepository;

    public List<Nozzle> getAllNozzles() {
        return nozzleRepository.findAllByScid(SecurityUtils.getScid());
    }

    public List<Nozzle> getActiveNozzles() {
        return nozzleRepository.findByActiveAndScid(true, SecurityUtils.getScid());
    }

    public List<Nozzle> getNozzlesByTank(Long tankId) {
        return nozzleRepository.findByTankIdAndScid(tankId, SecurityUtils.getScid());
    }

    public List<Nozzle> getNozzlesByPump(Long pumpId) {
        return nozzleRepository.findByPumpIdAndScid(pumpId, SecurityUtils.getScid());
    }

    public Nozzle getNozzleById(Long id) {
        return nozzleRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Nozzle not found with id: " + id));
    }

    public Nozzle createNozzle(Nozzle nozzle) {
        // Validate tank exists
        if (nozzle.getTank() != null && nozzle.getTank().getId() != null) {
            Tank tank = tankRepository.findById(nozzle.getTank().getId())
                    .orElseThrow(() -> new RuntimeException("Tank not found with id: " + nozzle.getTank().getId()));
            nozzle.setTank(tank);
        }
        // Validate pump exists
        if (nozzle.getPump() != null && nozzle.getPump().getId() != null) {
            Pump pump = pumpRepository.findById(nozzle.getPump().getId())
                    .orElseThrow(() -> new RuntimeException("Pump not found with id: " + nozzle.getPump().getId()));
            nozzle.setPump(pump);
        }
        if (nozzle.getScid() == null) {
            nozzle.setScid(SecurityUtils.getScid());
        }
        return nozzleRepository.save(nozzle);
    }

    public Nozzle updateNozzle(Long id, Nozzle nozzleDetails) {
        Nozzle nozzle = getNozzleById(id);
        nozzle.setNozzleName(nozzleDetails.getNozzleName());
        nozzle.setNozzleNumber(nozzleDetails.getNozzleNumber());
        nozzle.setNozzleCompany(nozzleDetails.getNozzleCompany());
        nozzle.setStampingExpiryDate(nozzleDetails.getStampingExpiryDate());
        if (nozzleDetails.getTank() != null && nozzleDetails.getTank().getId() != null) {
            Tank tank = tankRepository.findById(nozzleDetails.getTank().getId())
                    .orElseThrow(() -> new RuntimeException("Tank not found with id: " + nozzleDetails.getTank().getId()));
            nozzle.setTank(tank);
        }
        if (nozzleDetails.getPump() != null && nozzleDetails.getPump().getId() != null) {
            Pump pump = pumpRepository.findById(nozzleDetails.getPump().getId())
                    .orElseThrow(() -> new RuntimeException("Pump not found with id: " + nozzleDetails.getPump().getId()));
            nozzle.setPump(pump);
        }
        return nozzleRepository.save(nozzle);
    }

    public Nozzle toggleStatus(Long id) {
        Nozzle nozzle = getNozzleById(id);
        nozzle.setActive(!nozzle.isActive());
        return nozzleRepository.save(nozzle);
    }

    public void deleteNozzle(Long id) {
        Nozzle nozzle = getNozzleById(id);
        nozzleRepository.delete(nozzle);
    }
}
