package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    @Autowired
    private VehicleService vehicleService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<Vehicle> getAllVehicles(@RequestParam(required = false) String search) {
        return vehicleService.getAllVehicles(search);
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<Vehicle> searchVehicles(@RequestParam String q) {
        return vehicleService.searchVehicles(q);
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<Vehicle> getVehiclesByCustomerId(@PathVariable Long customerId) {
        return vehicleService.getVehiclesByCustomerId(customerId);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'VEHICLE_MANAGE')")
    public Vehicle createVehicle(@Valid @RequestBody Vehicle vehicle) {
        return vehicleService.createVehicle(vehicle);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_MANAGE')")
    public Vehicle updateVehicle(@PathVariable Long id, @Valid @RequestBody Vehicle vehicle) {
        return vehicleService.updateVehicle(id, vehicle);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_MANAGE')")
    public void deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'VEHICLE_MANAGE')")
    public Vehicle toggleStatus(@PathVariable Long id) {
        return vehicleService.toggleStatus(id);
    }

    @PatchMapping("/{id}/liter-limit")
    @PreAuthorize("hasPermission(null, 'VEHICLE_MANAGE')")
    public Vehicle updateLiterLimit(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        return vehicleService.updateLiterLimit(id, body.get("maxLitersPerMonth"));
    }
}
