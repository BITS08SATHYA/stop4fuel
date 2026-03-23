package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.repository.PumpRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PumpService {

    @Autowired
    private PumpRepository pumpRepository;

    public List<Pump> getAllPumps() {
        return pumpRepository.findAllByScid(SecurityUtils.getScid());
    }

    public List<Pump> getActivePumps() {
        return pumpRepository.findByActiveAndScid(true, SecurityUtils.getScid());
    }

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
        return pumpRepository.save(pump);
    }

    public Pump toggleStatus(Long id) {
        Pump pump = getPumpById(id);
        pump.setActive(!pump.isActive());
        return pumpRepository.save(pump);
    }

    public void deletePump(Long id) {
        Pump pump = getPumpById(id);
        pumpRepository.delete(pump);
    }
}
