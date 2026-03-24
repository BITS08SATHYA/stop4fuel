package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.TankInventory;
import com.stopforfuel.backend.exception.ReportGenerationException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TankDipReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    private volatile JasperReport compiledReport;

    @Value("${app.company.name:SATHYA FUELS}")
    private String companyName;

    @Value("${app.company.address:No. 45, GST Road, Tambaram, Chennai - 600045}")
    private String companyAddress;

    @Value("${app.company.phone:044-2234 5678}")
    private String companyPhone;

    public byte[] generatePdf(List<TankInventory> data, LocalDate fromDate, LocalDate toDate, String tankName) {
        try {
            JasperPrint print = fillReport(data, fromDate, toDate, tankName);
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Tank Dip PDF report", e);
        }
    }

    public byte[] generateExcel(List<TankInventory> data, LocalDate fromDate, LocalDate toDate, String tankName) {
        try {
            JasperPrint print = fillReport(data, fromDate, toDate, tankName);

            JRXlsxExporter exporter = new JRXlsxExporter();
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

            SimpleXlsxReportConfiguration config = new SimpleXlsxReportConfiguration();
            config.setOnePagePerSheet(false);
            config.setRemoveEmptySpaceBetweenRows(true);
            config.setDetectCellType(true);
            config.setWhitePageBackground(false);
            exporter.setConfiguration(config);

            exporter.exportReport();
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Tank Dip Excel report", e);
        }
    }

    private JasperPrint fillReport(List<TankInventory> data, LocalDate fromDate, LocalDate toDate, String tankName) throws Exception {
        JasperReport report = getCompiledReport();

        Map<String, Object> params = new HashMap<>();
        params.put("companyName", companyName);
        params.put("companyAddress", companyAddress);
        params.put("companyPhone", companyPhone);
        params.put("reportTitle", "Tank Dip Readings Report");
        params.put("tankName", tankName.replace("_", " "));
        params.put("fromDate", fromDate.format(DATE_FMT));
        params.put("toDate", toDate.format(DATE_FMT));
        params.put("generatedDate", java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a")));
        params.put("totalRecords", data.size());

        double totalSales = data.stream().mapToDouble(d -> d.getSaleStock() != null ? d.getSaleStock() : 0.0).sum();
        double totalIncome = data.stream().mapToDouble(d -> d.getIncomeStock() != null ? d.getIncomeStock() : 0.0).sum();
        params.put("totalSales", NUM_FMT.format(totalSales));
        params.put("totalIncome", NUM_FMT.format(totalIncome));

        int rowNum = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TankInventory inv : data) {
            Map<String, Object> row = new HashMap<>();
            row.put("rowNum", ++rowNum);
            row.put("date", inv.getDate().format(DATE_FMT));
            row.put("tankName", inv.getTank().getName());
            row.put("productName", inv.getTank().getProduct() != null ? inv.getTank().getProduct().getName() : "-");
            row.put("openDip", inv.getOpenDip() != null ? inv.getOpenDip() : "-");
            row.put("openStock", formatNum(inv.getOpenStock()));
            row.put("incomeStock", formatNum(inv.getIncomeStock()));
            row.put("closeDip", inv.getCloseDip() != null ? inv.getCloseDip() : "-");
            row.put("closeStock", formatNum(inv.getCloseStock()));
            row.put("saleStock", formatNum(inv.getSaleStock()));
            rows.add(row);
        }

        JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(rows);
        return JasperFillManager.fillReport(report, params, ds);
    }

    private JasperReport getCompiledReport() throws Exception {
        if (compiledReport == null) {
            synchronized (this) {
                if (compiledReport == null) {
                    InputStream is = new ClassPathResource("reports/tank_dip_report.jrxml").getInputStream();
                    compiledReport = JasperCompileManager.compileReport(is);
                }
            }
        }
        return compiledReport;
    }

    private String formatNum(Double value) {
        if (value == null) return "0.00";
        return NUM_FMT.format(value);
    }
}
