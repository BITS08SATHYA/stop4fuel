package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.DipChartDTO;
import com.stopforfuel.backend.entity.DipChart;
import com.stopforfuel.backend.entity.DipChartPoint;
import com.stopforfuel.backend.entity.Tank;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.DipChartRepository;
import com.stopforfuel.backend.repository.TankRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports a tank calibration (dip) chart from CSV. Two source shapes are
 * supported and normalised into one internal representation — litres at every
 * millimetre of dip:
 *
 * <ul>
 *   <li><b>PER_CM</b> (hsd.csv / ms.csv): {@code Dip_Value, <volume>, <diff>}.
 *       One row per centimetre; expanded to millimetres by linear interpolation.</li>
 *   <li><b>GRID</b> (xp.csv): {@code DipValue, t_0 … t_9}. A row per centimetre
 *       with the ten millimetre readings in columns; mapped in directly.</li>
 * </ul>
 *
 * Real charts contain transcription glitches that break the must-increase rule;
 * {@link #cleanAndValidate} detects and repairs them by interpolating between
 * the nearest valid neighbours, recording every fix in the chart's glitch log.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DipChartImportService {

    private final DipChartRepository chartRepository;
    private final TankRepository tankRepository;

    public enum ChartType { PER_CM, GRID }

    /** Mutable parse row keyed by its granularity unit (cm for PER_CM, mm for GRID). */
    private static final class RawPoint {
        final int key;
        double volume;
        boolean valid;
        RawPoint(int key, double volume, boolean valid) {
            this.key = key;
            this.volume = volume;
            this.valid = valid;
        }
    }

    @Transactional
    public DipChartDTO importChart(Long tankId, ChartType type, String volumeCol, InputStream csv) throws IOException {
        Long scid = SecurityUtils.getScid();
        Tank tank = tankRepository.findByIdAndScid(tankId, scid)
                .orElseThrow(() -> new BusinessException("Tank not found with id: " + tankId));

        List<String> glitchLog = new ArrayList<>();
        List<DipChartPoint> points = buildPoints(type, volumeCol, csv, glitchLog);

        if (points.isEmpty()) {
            throw new BusinessException("No valid calibration rows were found in the uploaded file.");
        }

        // One active chart per tank — drop any existing chart(s) first (cascade removes points).
        chartRepository.findByTankIdAndScid(tankId, scid).forEach(chartRepository::delete);

        DipChart chart = new DipChart();
        chart.setTank(tank);
        chart.setProduct(tank.getProduct());
        chart.setSourceFile(null);
        chart.setActive(true);
        chart.setMaxDipMm(points.get(points.size() - 1).getDipMm());
        chart.setHadGlitches(!glitchLog.isEmpty());
        chart.setGlitchLog(glitchLog.isEmpty() ? null : String.join("\n", glitchLog));
        for (DipChartPoint p : points) {
            p.setChart(chart);
        }
        chart.setPoints(points);

        DipChart saved = chartRepository.save(chart);
        if (saved.isHadGlitches()) {
            log.warn("Dip chart import for tank {} repaired {} glitched point(s).", tankId, glitchLog.size());
        }
        return DipChartDTO.from(saved, points.size());
    }

    /** Parse + clean + normalise a CSV into per-millimetre points (no DB access — unit testable). */
    List<DipChartPoint> buildPoints(ChartType type, String volumeCol, InputStream csv, List<String> glitchLog) throws IOException {
        if (type == ChartType.GRID) {
            List<RawPoint> raw = parseGrid(csv);
            cleanAndValidate(raw, glitchLog);
            return gridToPoints(raw);
        }
        if (volumeCol == null || volumeCol.isBlank()) {
            throw new BusinessException("volumeCol is required for a PER_CM chart (e.g. Dip_Volume / Dip_Stock).");
        }
        List<RawPoint> raw = parsePerCm(csv, volumeCol.trim());
        cleanAndValidate(raw, glitchLog);
        return expandCmToMm(raw);
    }

    // --- Parsing ---------------------------------------------------------

    private List<RawPoint> parsePerCm(InputStream csv, String volumeCol) throws IOException {
        List<RawPoint> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new BusinessException("Empty CSV file.");
            String[] cols = splitCsv(header);
            int volIdx = indexOf(cols, volumeCol);
            if (volIdx < 0) {
                throw new BusinessException("Column '" + volumeCol + "' not found. Header was: " + header);
            }
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = splitCsv(line);
                Integer cm = tryInt(f, 0);
                if (cm == null) continue; // skip rows without a dip value
                Double vol = tryDouble(f, volIdx);
                out.add(new RawPoint(cm, vol != null ? vol : Double.NaN, vol != null));
            }
        }
        return out;
    }

    private List<RawPoint> parseGrid(InputStream csv) throws IOException {
        List<RawPoint> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String header = br.readLine();
            if (header == null) throw new BusinessException("Empty CSV file.");
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = splitCsv(line);
                Integer cm = tryInt(f, 0);
                if (cm == null) continue;
                for (int d = 0; d <= 9; d++) {
                    int mm = cm * 10 + d;
                    Double vol = tryDouble(f, 1 + d);
                    out.add(new RawPoint(mm, vol != null ? vol : Double.NaN, vol != null));
                }
            }
        }
        return out;
    }

    // --- Glitch detection + repair --------------------------------------

    /**
     * Sorts the points and enforces a non-decreasing volume curve. A point is
     * treated as bad when it failed to parse, dropped below the last good
     * value, or repeats a non-zero value (interior flat). Bad runs are repaired
     * by linear interpolation between the surrounding good anchors; every fix is
     * appended to {@code glitchLog}.
     */
    void cleanAndValidate(List<RawPoint> pts, List<String> glitchLog) {
        pts.sort((a, b) -> Integer.compare(a.key, b.key));
        int n = pts.size();
        if (n == 0) return;

        boolean[] bad = new boolean[n];
        double lastGood = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            RawPoint p = pts.get(i);
            if (!p.valid || Double.isNaN(p.volume)) {
                bad[i] = true;
                continue;
            }
            if (lastGood != Double.NEGATIVE_INFINITY) {
                if (p.volume < lastGood - 1e-9) {            // dropped below a higher earlier value
                    bad[i] = true;
                    continue;
                }
                if (Math.abs(p.volume - lastGood) < 1e-9 && p.volume > 1e-9) { // interior flat (non-zero)
                    bad[i] = true;
                    continue;
                }
            }
            lastGood = p.volume;
        }

        int i = 0;
        while (i < n) {
            if (!bad[i]) { i++; continue; }
            int start = i;
            while (i < n && bad[i]) i++;
            int end = i - 1;
            repairRun(pts, bad, start, end, n, glitchLog);
        }
    }

    private void repairRun(List<RawPoint> pts, boolean[] bad, int start, int end, int n, List<String> glitchLog) {
        Integer lo = start - 1 >= 0 ? start - 1 : null;
        Integer hi = end + 1 < n ? end + 1 : null;
        for (int j = start; j <= end; j++) {
            RawPoint p = pts.get(j);
            double repaired;
            if (lo != null && hi != null) {
                RawPoint a = pts.get(lo), b = pts.get(hi);
                double t = (double) (p.key - a.key) / (b.key - a.key);
                repaired = a.volume + (b.volume - a.volume) * t;
            } else if (lo != null) {
                repaired = pts.get(lo).volume;      // trailing bad run — hold last good
            } else if (hi != null) {
                repaired = pts.get(hi).volume;      // leading bad run — hold first good
            } else {
                repaired = 0.0;
            }
            glitchLog.add(String.format("dip %d.%d: %s -> %.3f L",
                    p.key / 10, p.key % 10,
                    (!p.valid || Double.isNaN(p.volume)) ? "unparseable" : String.format("%.3f", p.volume),
                    repaired));
            p.volume = repaired;
            p.valid = true;
            bad[j] = false;
        }
    }

    // --- Normalisation to millimetre points -----------------------------

    private List<DipChartPoint> expandCmToMm(List<RawPoint> cm) {
        List<DipChartPoint> out = new ArrayList<>();
        for (int i = 0; i < cm.size(); i++) {
            RawPoint a = cm.get(i);
            int mmA = a.key * 10;
            if (i < cm.size() - 1) {
                RawPoint b = cm.get(i + 1);
                int mmB = b.key * 10;
                for (int mm = mmA; mm < mmB; mm++) {
                    double t = (double) (mm - mmA) / (mmB - mmA);
                    out.add(point(mm, a.volume + (b.volume - a.volume) * t));
                }
            } else {
                out.add(point(mmA, a.volume));
            }
        }
        return out;
    }

    private List<DipChartPoint> gridToPoints(List<RawPoint> mm) {
        List<DipChartPoint> out = new ArrayList<>(mm.size());
        for (RawPoint p : mm) {
            out.add(point(p.key, p.volume));
        }
        return out;
    }

    private DipChartPoint point(int dipMm, double volume) {
        DipChartPoint p = new DipChartPoint();
        p.setDipMm(dipMm);
        p.setVolumeLitres(Math.round(volume * 1000.0) / 1000.0);
        return p;
    }

    // --- CSV helpers -----------------------------------------------------

    private String[] splitCsv(String line) {
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private int indexOf(String[] cols, String name) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private Integer tryInt(String[] f, int idx) {
        if (idx >= f.length || f[idx].isEmpty()) return null;
        try {
            return (int) Math.round(Double.parseDouble(f[idx]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double tryDouble(String[] f, int idx) {
        if (idx >= f.length || f[idx].isEmpty()) return null;
        try {
            return Double.parseDouble(f[idx]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
