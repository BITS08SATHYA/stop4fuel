package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@CrossOrigin(origins = "*")
public class VehicleController {

    @Autowired
    private VehicleService vehicleService;

    @GetMapping
    public List<Vehicle> getAllVehicles(@RequestParam(required = false) String search) {
        return vehicleService.getAllVehicles(search);
    }

    @GetMapping("/search")
    public List<Vehicle> searchVehicles(@RequestParam String q) {
        return vehicleService.searchVehicles(q);
    }

    @GetMapping("/customer/{customerId}")
    public List<Vehicle> getVehiclesByCustomerId(@PathVariable Long customerId) {
        return vehicleService.getVehiclesByCustomerId(customerId);
    }

    @PostMapping
    public Vehicle createVehicle(@RequestBody Vehicle vehicle) {
        return vehicleService.createVehicle(vehicle);
    }

    @PutMapping("/{id}")
    public Vehicle updateVehicle(@PathVariable Long id, @RequestBody Vehicle vehicle) {
        return vehicleService.updateVehicle(id, vehicle);
    }

    @DeleteMapping("/{id}")
    public void deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
    }

    @PatchMapping("/{id}/toggle-status")
    public Vehicle toggleStatus(@PathVariable Long id) {
        return vehicleService.toggleStatus(id);
    }
}
