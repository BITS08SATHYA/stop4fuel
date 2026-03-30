package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.TankInventoryDTO;
import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.service.TankInventoryService;
import com.stopforfuel.backend.service.TankDipReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/inventory/tanks")
@RequiredArgsConstructor
public class TankInventoryController {

    private final TankInventoryService service;
    private final TankDipReportService reportService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<TankInventoryDTO> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long tankId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<TankInventory> result;
        if (fromDate != null && toDate != null) {
            if (tankId != null) result = service.getByTankAndDateRange(tankId, fromDate, toDate);
            else result = service.getByDateRange(fromDate, toDate);
        } else if (date != null) result = service.getByDate(date);
        else if (tankId != null) result = service.getByTankId(tankId);
        else result = service.getAll();
        return result.stream().map(TankInventoryDTO::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public TankInventoryDTO getById(@PathVariable Long id) {
        return TankInventoryDTO.from(service.getById(id));
    }

    @GetMapping("/report")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long tankId,
            @RequestParam(defaultValue = "pdf") String format) {

        List<TankInventory> data = tankId != null
                ? service.getByTankAndDateRange(tankId, fromDate, toDate)
                : service.getByDateRange(fromDate, toDate);

        String tankName = (tankId != null && !data.isEmpty()) ? data.get(0).getTank().getName() : "All_Tanks";
        String dateRange = fromDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")) + "_" + toDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"));

        if ("excel".equalsIgnoreCase(format)) {
            byte[] bytes = reportService.generateExcel(data, fromDate, toDate, tankName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=TankDip_" + tankName + "_" + dateRange + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } else {
            byte[] bytes = reportService.generatePdf(data, fromDate, toDate, tankName);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=TankDip_" + tankName + "_" + dateRange + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        }
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public TankInventoryDTO create(@Valid @RequestBody TankInventory inventory) {
        return TankInventoryDTO.from(service.save(inventory));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public TankInventoryDTO update(@PathVariable Long id, @Valid @RequestBody TankInventory inventory) {
        return TankInventoryDTO.from(service.update(id, inventory));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
