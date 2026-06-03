package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.DipChart;
import com.stopforfuel.backend.entity.DipChartPoint;
import com.stopforfuel.backend.repository.DipChartPointRepository;
import com.stopforfuel.backend.repository.DipChartRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Converts a physical dip reading to a stock volume using a tank's calibration
 * chart. Dip values may be whole centimetres ({@code 100}) or one-decimal
 * cm+mm ({@code 100.5}); both are handled by linear interpolation over the
 * per-millimetre points.
 */
@Service
@RequiredArgsConstructor
public class DipChartService {

    private final DipChartRepository chartRepository;
    private final DipChartPointRepository pointRepository;

    @Transactional(readOnly = true)
    public boolean hasChart(Long tankId) {
        return chartRepository.findByTankIdAndScidAndActiveTrue(tankId, SecurityUtils.getScid()).isPresent();
    }

    /**
     * Returns the stock in litres for the given dip (in cm) on the tank's active
     * chart, or empty when the tank has no chart (caller falls back to manual entry).
     */
    @Transactional(readOnly = true)
    public Optional<Double> dipToVolume(Long tankId, double dipValueCm) {
        Optional<DipChart> chartOpt = chartRepository.findByTankIdAndScidAndActiveTrue(tankId, SecurityUtils.getScid());
        if (chartOpt.isEmpty()) return Optional.empty();
        List<DipChartPoint> points = pointRepository.findByChartIdOrderByDipMmAsc(chartOpt.get().getId());
        if (points.isEmpty()) return Optional.empty();
        return Optional.of(interpolate(points, dipValueCm * 10.0));
    }

    static double interpolate(List<DipChartPoint> points, double mm) {
        DipChartPoint first = points.get(0);
        DipChartPoint last = points.get(points.size() - 1);
        if (mm <= first.getDipMm()) return first.getVolumeLitres();
        if (mm >= last.getDipMm()) return last.getVolumeLitres();

        // Largest index whose dipMm <= mm.
        int lo = 0, hi = points.size() - 1;
        while (lo < hi) {
            int midIdx = (lo + hi + 1) >>> 1;
            if (points.get(midIdx).getDipMm() <= mm) lo = midIdx;
            else hi = midIdx - 1;
        }
        DipChartPoint a = points.get(lo);
        if (a.getDipMm() == mm || lo == points.size() - 1) return a.getVolumeLitres();
        DipChartPoint b = points.get(lo + 1);
        double t = (mm - a.getDipMm()) / (b.getDipMm() - a.getDipMm());
        return a.getVolumeLitres() + (b.getVolumeLitres() - a.getVolumeLitres()) * t;
    }
}
