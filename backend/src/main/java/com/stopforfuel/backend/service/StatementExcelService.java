package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Statement;
import com.stopforfuel.backend.exception.ReportGenerationException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StatementExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,##0.00");

    public byte[] generateExcel(List<Statement> statements) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Statements");

            // Header style
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 220, (byte) 220, (byte) 220}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Cell style
            XSSFCellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);
            XSSFFont cellFont = workbook.createFont();
            cellFont.setFontHeightInPoints((short) 9);
            cellStyle.setFont(cellFont);

            // Number style
            XSSFCellStyle numStyle = workbook.createCellStyle();
            numStyle.cloneStyleFrom(cellStyle);
            numStyle.setAlignment(HorizontalAlignment.RIGHT);
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // Header row
            String[] headers = {"Statement No", "Customer", "From Date", "To Date", "Statement Date", "Bills", "Net Amount", "Received", "Balance", "Status"};
            XSSFRow headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIdx = 1;
            for (Statement s : statements) {
                XSSFRow row = sheet.createRow(rowIdx++);

                XSSFCell c0 = row.createCell(0);
                c0.setCellValue(s.getStatementNo());
                c0.setCellStyle(cellStyle);

                XSSFCell c1 = row.createCell(1);
                c1.setCellValue(s.getCustomer() != null ? s.getCustomer().getName() : "");
                c1.setCellStyle(cellStyle);

                XSSFCell c2 = row.createCell(2);
                c2.setCellValue(s.getFromDate() != null ? s.getFromDate().format(DATE_FMT) : "");
                c2.setCellStyle(cellStyle);

                XSSFCell c3 = row.createCell(3);
                c3.setCellValue(s.getToDate() != null ? s.getToDate().format(DATE_FMT) : "");
                c3.setCellStyle(cellStyle);

                XSSFCell c4 = row.createCell(4);
                c4.setCellValue(s.getStatementDate() != null ? s.getStatementDate().format(DATE_FMT) : "");
                c4.setCellStyle(cellStyle);

                XSSFCell c5 = row.createCell(5);
                c5.setCellValue(s.getNumberOfBills() != null ? s.getNumberOfBills() : 0);
                c5.setCellStyle(cellStyle);

                XSSFCell c6 = row.createCell(6);
                c6.setCellValue(toBigDecimalDouble(s.getNetAmount()));
                c6.setCellStyle(numStyle);

                XSSFCell c7 = row.createCell(7);
                c7.setCellValue(toBigDecimalDouble(s.getReceivedAmount()));
                c7.setCellStyle(numStyle);

                XSSFCell c8 = row.createCell(8);
                c8.setCellValue(toBigDecimalDouble(s.getBalanceAmount()));
                c8.setCellStyle(numStyle);

                XSSFCell c9 = row.createCell(9);
                c9.setCellValue(s.getStatus() != null ? s.getStatus() : "");
                c9.setCellStyle(cellStyle);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Statement Excel report", e);
        }
    }

    private double toBigDecimalDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }
}
