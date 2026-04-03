package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.OilType;
import com.stopforfuel.backend.repository.OilTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OilTypeService {

    private final OilTypeRepository repository;

    @Transactional(readOnly = true)
    public List<OilType> getAllOilTypes() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<OilType> getActiveOilTypes() {
        return repository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public OilType getOilTypeById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("OilType not found with id: " + id));
    }

    public OilType createOilType(OilType oilType) {
        return repository.save(oilType);
    }

    public OilType updateOilType(Long id, OilType details) {
        OilType oilType = getOilTypeById(id);
        oilType.setName(details.getName());
        oilType.setDescription(details.getDescription());
        oilType.setActive(details.isActive());
        return repository.save(oilType);
    }

    public OilType toggleStatus(Long id) {
        OilType oilType = getOilTypeById(id);
        oilType.setActive(!oilType.isActive());
        return repository.save(oilType);
    }

    public void deleteOilType(Long id) {
        repository.deleteById(id);
    }
}
