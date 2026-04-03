package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.StockAlertDTO;
import com.stopforfuel.backend.entity.StockAlert;
import com.stopforfuel.backend.service.StockAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-alerts")
public class StockAlertController {

    @Autowired
    private StockAlertService stockAlertService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<StockAlertDTO> getActiveAlerts() {
        return stockAlertService.getActiveAlerts().stream().map(StockAlertDTO::from).toList();
    }

    @GetMapping("/all")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<StockAlertDTO> getAllAlerts() {
        return stockAlertService.getAllAlerts().stream().map(StockAlertDTO::from).toList();
    }

    @PostMapping("/check")
    @PreAuthorize("hasPermission(null, 'STATION_VIEW')")
    public List<StockAlertDTO> checkAlerts() {
        return stockAlertService.checkAndGenerateAlerts().stream().map(StockAlertDTO::from).toList();
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public StockAlertDTO acknowledgeAlert(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String acknowledgedBy = body != null ? body.getOrDefault("acknowledgedBy", "MANUAL") : "MANUAL";
        return StockAlertDTO.from(stockAlertService.acknowledgeAlert(id, acknowledgedBy));
    }

    @PostMapping("/acknowledge-all")
    @PreAuthorize("hasPermission(null, 'STATION_MANAGE')")
    public ResponseEntity<?> acknowledgeAll(@RequestBody(required = false) Map<String, String> body) {
        String acknowledgedBy = body != null ? body.getOrDefault("acknowledgedBy", "MANUAL") : "MANUAL";
        stockAlertService.acknowledgeAllAlerts(acknowledgedBy);
        return ResponseEntity.ok().build();
    }
}
