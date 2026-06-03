package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.DipChart;

import java.time.LocalDateTime;

/**
 * Summary view of a {@link DipChart} (metadata only — not the thousands of
 * individual calibration points). Used by the admin chart-management UI and
 * returned from the import endpoint.
 */
public record DipChartDTO(
        Long id,
        Long tankId,
        String tankName,
        Long productId,
        String productName,
        String sourceFile,
        Integer maxDipMm,
        int pointCount,
        boolean active,
        boolean hadGlitches,
        int glitchesRepaired,
        String glitchLog,
        LocalDateTime createdAt
) {
    public static DipChartDTO from(DipChart c, int pointCount) {
        String log = c.getGlitchLog();
        int glitchesRepaired = (log == null || log.isBlank()) ? 0 : log.strip().split("\n").length;
        return new DipChartDTO(
                c.getId(),
                c.getTank() != null ? c.getTank().getId() : null,
                c.getTank() != null ? c.getTank().getName() : null,
                c.getProduct() != null ? c.getProduct().getId() : null,
                c.getProduct() != null ? c.getProduct().getName() : null,
                c.getSourceFile(),
                c.getMaxDipMm(),
                pointCount,
                c.isActive(),
                c.isHadGlitches(),
                glitchesRepaired,
                c.getGlitchLog(),
                c.getCreatedAt()
        );
    }
}
