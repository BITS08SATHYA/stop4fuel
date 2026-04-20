package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.VehicleDTO;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    public Page<VehicleDTO> getAllVehicles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EntityStatus status,
            @RequestParam(required = false) Long customerId,
            @PageableDefault(size = 25, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return vehicleService.searchPaged(search, status, customerId, pageable).map(VehicleDTO::from);
    }

    @GetMapping("/search")
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<VehicleDTO> searchVehicles(@RequestParam String q) {
        return vehicleService.searchVehicles(q).stream().map(VehicleDTO::from).toList();
    }

    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_VIEW')")
    public List<VehicleDTO> getVehiclesByCustomerId(@PathVariable Long customerId) {
        return vehicleService.getVehiclesByCustomerId(customerId).stream().map(VehicleDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'VEHICLE_CREATE')")
    public VehicleDTO createVehicle(@Valid @RequestBody Vehicle vehicle) {
        return VehicleDTO.from(vehicleService.createVehicle(vehicle));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO updateVehicle(@PathVariable Long id, @Valid @RequestBody Vehicle vehicle) {
        return VehicleDTO.from(vehicleService.updateVehicle(id, vehicle));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'VEHICLE_DELETE')")
    public void deleteVehicle(@PathVariable Long id) {
        vehicleService.deleteVehicle(id);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO toggleStatus(@PathVariable Long id) {
        return VehicleDTO.from(vehicleService.toggleStatus(id));
    }

    @PatchMapping("/{id}/block")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO blockVehicle(@PathVariable Long id) {
        return VehicleDTO.from(vehicleService.blockVehicle(id));
    }

    @PatchMapping("/{id}/unblock")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO unblockVehicle(@PathVariable Long id) {
        return VehicleDTO.from(vehicleService.unblockVehicle(id));
    }

    @PatchMapping("/{id}/liter-limit")
    @PreAuthorize("hasPermission(null, 'VEHICLE_UPDATE')")
    public VehicleDTO updateLiterLimit(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        return VehicleDTO.from(vehicleService.updateLiterLimit(id, body.get("maxLitersPerMonth")));
    }
}
