package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.DipChartPoint;
import com.stopforfuel.backend.service.DipChartImportService.ChartType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for CSV parsing, glitch detection/repair, and mm normalisation. */
class DipChartImportServiceTest {

    private final DipChartImportService service = new DipChartImportService(null, null);

    private static InputStream csv(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static double volAtMm(List<DipChartPoint> pts, int mm) {
        return pts.stream().filter(p -> p.getDipMm() == mm).findFirst()
                .orElseThrow(() -> new AssertionError("no point at mm " + mm))
                .getVolumeLitres();
    }

    private static void assertMonotonic(List<DipChartPoint> pts) {
        for (int i = 1; i < pts.size(); i++) {
            assertTrue(pts.get(i).getVolumeLitres() >= pts.get(i - 1).getVolumeLitres() - 1e-6,
                    "volume must not decrease at mm " + pts.get(i).getDipMm());
        }
    }

    @Test
    void perCm_expandsToMillimetresAndLooksUpExactValue() throws IOException {
        String data = """
                Dip_Value,Dip_Volume,Dip_Diff
                99,9634.96,12.58
                100,9760.78,12.58
                101,9886.66,12.59
                """;
        List<String> glitches = new ArrayList<>();
        List<DipChartPoint> pts = service.buildPoints(ChartType.PER_CM, "Dip_Volume", csv(data), glitches);

        assertTrue(glitches.isEmpty(), "clean data has no glitches");
        assertEquals(9760.78, volAtMm(pts, 1000), 0.001);   // dip 100.0 cm
        assertEquals(9823.72, volAtMm(pts, 1005), 0.01);    // dip 100.5 cm interpolated
        assertMonotonic(pts);
    }

    @Test
    void perCm_repairsInteriorFlatAndDrop() throws IOException {
        String data = """
                Dip_Value,Dip_Volume,Dip_Diff
                1,100,10
                2,200,10
                3,200,0
                4,400,20
                5,350,-5
                6,600,25
                """;
        List<String> glitches = new ArrayList<>();
        List<DipChartPoint> pts = service.buildPoints(ChartType.PER_CM, "Dip_Volume", csv(data), glitches);

        assertEquals(2, glitches.size(), "one flat (cm3) + one drop (cm5)");
        assertEquals(300.0, volAtMm(pts, 30), 0.001);  // cm3 repaired between 200 and 400
        assertEquals(500.0, volAtMm(pts, 50), 0.001);  // cm5 repaired between 400 and 600
        assertMonotonic(pts);
    }

    @Test
    void grid_mapsMillimetreColumnsDirectly() throws IOException {
        String data = """
                DipValue,t_0,t_1,t_2,t_3,t_4,t_5,t_6,t_7,t_8,t_9
                50,3300.827,3309.564,3318.301,3327.288,3336.275,3345.258,3354.249,3363.239,3372.233,3381.2195
                51,3390.206,3399.246,3408.286,3417.826,3427.366,3436.903,3446.446,3455.483,3464.526,3472.5705
                """;
        List<String> glitches = new ArrayList<>();
        List<DipChartPoint> pts = service.buildPoints(ChartType.GRID, null, csv(data), glitches);

        assertTrue(glitches.isEmpty());
        assertEquals(3300.827, volAtMm(pts, 500), 0.001);  // dip 50.0 cm
        assertEquals(3327.288, volAtMm(pts, 503), 0.001);  // dip 50.3 cm — exact grid hit
        assertMonotonic(pts);
    }

    @Test
    void grid_repairsInteriorDropCell() throws IOException {
        // t_7 (173.895) is a transcription drop, like the real xp.csv row 10.
        String data = """
                DipValue,t_0,t_1,t_2,t_3,t_4,t_5,t_6,t_7,t_8,t_9
                10,341.0,342.0,343.0,344.0,345.0,346.0,347.0,173.895,349.0,350.0
                """;
        List<String> glitches = new ArrayList<>();
        List<DipChartPoint> pts = service.buildPoints(ChartType.GRID, null, csv(data), glitches);

        assertEquals(1, glitches.size());
        assertEquals(348.0, volAtMm(pts, 107), 0.001);  // repaired between 347 (t_6) and 349 (t_8)
        assertMonotonic(pts);
    }

    @Test
    void grid_leadingZerosAreNotTreatedAsGlitches() throws IOException {
        String data = """
                DipValue,t_0,t_1,t_2,t_3,t_4,t_5,t_6,t_7,t_8,t_9
                1,0,0,0,0,0,0,0,0,0,0
                2,27.175,29.443,31.711,33.979,36.247,38.515,40.783,43.051,45.319,47.5865
                """;
        List<String> glitches = new ArrayList<>();
        List<DipChartPoint> pts = service.buildPoints(ChartType.GRID, null, csv(data), glitches);

        assertTrue(glitches.isEmpty(), "zero floor must not be flagged");
        assertEquals(0.0, volAtMm(pts, 10), 0.001);
        assertEquals(27.175, volAtMm(pts, 20), 0.001);
        assertMonotonic(pts);
    }

    @Test
    void grid_repairsUnparseableCell() throws IOException {
        String data = """
                DipValue,t_0,t_1,t_2,t_3,t_4,t_5,t_6,t_7,t_8,t_9
                10,341.0,342.0,343.0,,345.0,346.0,347.0,348.0,349.0,350.0
                """;
        List<String> glitches = new ArrayList<>();
        List<DipChartPoint> pts = service.buildPoints(ChartType.GRID, null, csv(data), glitches);

        assertEquals(1, glitches.size());
        assertEquals(344.0, volAtMm(pts, 103), 0.001);  // empty cell repaired between 343 and 345
        assertMonotonic(pts);
    }
}
