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

/** Unit tests for the dip→volume interpolation, built on real-chart sample rows. */
class DipChartServiceTest {

    private final DipChartImportService importer = new DipChartImportService(null, null);

    private static InputStream csv(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private List<DipChartPoint> build(ChartType type, String volCol, String data) throws IOException {
        return importer.buildPoints(type, volCol, csv(data), new ArrayList<>());
    }

    @Test
    void hsd_wholeAndFractionalCm() throws IOException {
        List<DipChartPoint> pts = build(ChartType.PER_CM, "Dip_Volume", """
                Dip_Value,Dip_Volume,Dip_Diff
                99,9634.96,12.58
                100,9760.78,12.58
                101,9886.66,12.59
                """);
        assertEquals(9760.78, DipChartService.interpolate(pts, 100.0 * 10), 0.001);
        assertEquals(9823.72, DipChartService.interpolate(pts, 100.5 * 10), 0.01);
    }

    @Test
    void ms_wholeCm() throws IOException {
        List<DipChartPoint> pts = build(ChartType.PER_CM, "Dip_Stock", """
                Dip_Value,Dip_Stock,Stock_Diff
                99,8309.51,10.63
                100,8415.78,10.63
                101,8522.01,10.62
                """);
        assertEquals(8415.78, DipChartService.interpolate(pts, 100.0 * 10), 0.001);
    }

    @Test
    void xp_gridExactMillimetre() throws IOException {
        List<DipChartPoint> pts = build(ChartType.GRID, null, """
                DipValue,t_0,t_1,t_2,t_3,t_4,t_5,t_6,t_7,t_8,t_9
                50,3300.827,3309.564,3318.301,3327.288,3336.275,3345.258,3354.249,3363.239,3372.233,3381.2195
                51,3390.206,3399.246,3408.286,3417.826,3427.366,3436.903,3446.446,3455.483,3464.526,3472.5705
                """);
        assertEquals(3300.827, DipChartService.interpolate(pts, 50.0 * 10), 0.001);
        assertEquals(3327.288, DipChartService.interpolate(pts, 50.3 * 10), 0.001);
    }

    @Test
    void clampsBelowMinAndAboveMax() throws IOException {
        List<DipChartPoint> pts = build(ChartType.PER_CM, "Dip_Volume", """
                Dip_Value,Dip_Volume,Dip_Diff
                99,9634.96,12.58
                100,9760.78,12.58
                101,9886.66,12.59
                """);
        // min point is mm 990, max is mm 1010
        assertEquals(9634.96, DipChartService.interpolate(pts, 10), 0.001);     // far below range
        assertEquals(9886.66, DipChartService.interpolate(pts, 99999), 0.001);  // far above range
    }
}
