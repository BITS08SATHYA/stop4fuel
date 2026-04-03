package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.repository.NozzleRepository;
import com.stopforfuel.backend.repository.PumpRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PumpService {

    private final PumpRepository pumpRepository;

    private final NozzleRepository nozzleRepository;

    @Transactional(readOnly = true)
    public List<Pump> getAllPumps() {
        return pumpRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<Pump> getActivePumps() {
        return pumpRepository.findByActiveAndScid(true, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public Pump getPumpById(Long id) {
        return pumpRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Pump not found with id: " + id));
    }

    public Pump createPump(Pump pump) {
        if (pump.getScid() == null) {
            pump.setScid(SecurityUtils.getScid());
        }
        return pumpRepository.save(pump);
    }

    public Pump updatePump(Long id, Pump pumpDetails) {
        Pump pump = getPumpById(id);
        pump.setName(pumpDetails.getName());
        // Cascade: deactivate connected nozzles when pump is set inactive
        if (!pumpDetails.isActive() && pump.isActive()) {
            deactivateConnectedNozzles(id);
        }
        pump.setActive(pumpDetails.isActive());
        return pumpRepository.save(pump);
    }

    public Pump toggleStatus(Long id) {
        Pump pump = getPumpById(id);
        boolean newStatus = !pump.isActive();
        // Cascade: deactivate connected nozzles when pump is set inactive
        if (!newStatus) {
            deactivateConnectedNozzles(id);
        }
        pump.setActive(newStatus);
        return pumpRepository.save(pump);
    }

    private void deactivateConnectedNozzles(Long pumpId) {
        List<Nozzle> connectedNozzles = nozzleRepository.findByPumpIdAndScid(pumpId, SecurityUtils.getScid());
        for (Nozzle nozzle : connectedNozzles) {
            if (nozzle.isActive()) {
                nozzle.setActive(false);
                nozzleRepository.save(nozzle);
            }
        }
    }

    @Transactional
    public void deletePump(Long id) {
        Pump pump = getPumpById(id);
        pump.setActive(false);
        // Cascade: deactivate connected nozzles
        deactivateConnectedNozzles(id);
        pumpRepository.save(pump);
    }
}
