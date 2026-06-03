package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.DipChartDTO;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.DipChartPointRepository;
import com.stopforfuel.backend.repository.DipChartRepository;
import com.stopforfuel.backend.service.DipChartImportService;
import com.stopforfuel.backend.service.DipChartImportService.ChartType;
import com.stopforfuel.backend.service.DipChartService;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Dip-chart (tank calibration) management and dip→litres conversion.
 * Conversion is read-open to anyone who can view inventory (cashiers); import
 * and delete require inventory create/delete (owner/admin).
 */
@RestController
@RequestMapping("/api/dip-charts")
@RequiredArgsConstructor
public class DipChartController {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final DipChartService dipChartService;
    private final DipChartImportService importService;
    private final DipChartRepository chartRepository;
    private final DipChartPointRepository pointRepository;

    public record ConvertResponse(Long tankId, double dip, Double volume, boolean hasChart) {}

    @GetMapping("/convert")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ConvertResponse convert(@RequestParam Long tankId, @RequestParam double dip) {
        Optional<Double> volume = dipChartService.dipToVolume(tankId, dip);
        return new ConvertResponse(tankId, dip, volume.orElse(null), volume.isPresent());
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<DipChartDTO> list() {
        return chartRepository.findAllByScid(SecurityUtils.getScid()).stream()
                .map(c -> DipChartDTO.from(c, (int) pointRepository.countByChartId(c.getId())))
                .toList();
    }

    @GetMapping("/tank/{tankId}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public ResponseEntity<DipChartDTO> getByTank(@PathVariable Long tankId) {
        return chartRepository.findByTankIdAndScidAndActiveTrue(tankId, SecurityUtils.getScid())
                .map(c -> ResponseEntity.ok(DipChartDTO.from(c, (int) pointRepository.countByChartId(c.getId()))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(path = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasPermission(null, 'INVENTORY_CREATE')")
    public DipChartDTO importChart(@RequestParam Long tankId,
                                   @RequestParam ChartType type,
                                   @RequestParam(required = false) String volumeCol,
                                   @RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("A CSV file is required.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException("File exceeds the 5 MB limit.");
        }
        return importService.importChart(tankId, type, volumeCol, file.getInputStream());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        chartRepository.findByIdAndScid(id, SecurityUtils.getScid()).ifPresent(chartRepository::delete);
        return ResponseEntity.noContent().build();
    }
}
