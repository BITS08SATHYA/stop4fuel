package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.VehicleTypeDTO;
import com.stopforfuel.backend.entity.VehicleType;
import com.stopforfuel.backend.repository.VehicleTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-types")
public class VehicleTypeController {

    @Autowired
    private VehicleTypeRepository vehicleTypeRepository;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<VehicleType> getAllVehicleTypes() {
        return vehicleTypeRepository.findAll();
    }
}
