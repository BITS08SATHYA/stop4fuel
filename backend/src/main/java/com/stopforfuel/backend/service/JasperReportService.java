package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.exception.ReportGenerationException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JasperReportService {

    private final Map<String, JasperReport> compiledReports = new ConcurrentHashMap<>();

    @Value("${app.company.name:SATHYA FUELS}")
    private String companyName;

    @Value("${app.company.address:No. 45, GST Road, Tambaram, Chennai - 600045}")
    private String companyAddress;

    @Value("${app.company.gstin:33AABCS1234F1Z5}")
    private String companyGstin;

    @Value("${app.company.phone:044-2234 5678}")
    private String companyPhone;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm a");
    private static final java.text.DecimalFormat NUM_FMT = new java.text.DecimalFormat("#,##0.00");

    public byte[] generateCustomerVehicleReport(Customer customer, List<Vehicle> vehicles) {
        try {
            JasperReport report = getCompiledReport("reports/customer_vehicle_report.jrxml");

            Map<String, Object> params = new HashMap<>();
            params.put("companyName", companyName);
            params.put("companyAddress", companyAddress);
            params.put("companyGstin", companyGstin);
            params.put("companyPhone", companyPhone);
            params.put("generatedDate", LocalDateTime.now().format(DATETIME_FMT));

            params.put("customerName", customer.getName() != null ? customer.getName() : "—");
            params.put("groupName", customer.getGroup() != null ? customer.getGroup().getGroupName() : "—");
            params.put("partyType", customer.getParty() != null ? customer.getParty().getPartyType() : "—");

            Set<String> emails = customer.getEmails();
            params.put("customerEmail", emails != null && !emails.isEmpty() ? String.join(", ", emails) : "—");

            Set<String> phones = customer.getPhoneNumbers();
            params.put("customerPhone", phones != null && !phones.isEmpty() ? String.join(", ", phones) : "—");

            params.put("customerAddress", customer.getAddress() != null ? customer.getAddress() : "—");

            LocalDate joinDate = customer.getJoinDate();
            params.put("joinDate", joinDate != null ? joinDate.format(DATE_FMT) : "—");

            params.put("creditLimitAmount", formatNumber(customer.getCreditLimitAmount()));
            params.put("creditLimitLiters", formatNumber(customer.getCreditLimitLiters()));
            params.put("consumedLiters", formatNumber(customer.getConsumedLiters()));
            params.put("customerStatus", customer.getStatus() != null ? customer.getStatus() : "ACTIVE");

            // Vehicle stats
            int active = 0, inactive = 0, blocked = 0;
            BigDecimal totalCapacity = BigDecimal.ZERO;
            BigDecimal totalMonthly = BigDecimal.ZERO;
            BigDecimal totalConsumed = BigDecimal.ZERO;

            int rowCounter = 0;
            List<Map<String, Object>> vehicleData = new ArrayList<>();

            for (Vehicle v : vehicles) {
                Map<String, Object> row = new HashMap<>();
                row.put("rowNum", ++rowCounter);
                row.put("vehicleNumber", v.getVehicleNumber());
                row.put("vehicleType", v.getVehicleType() != null ? v.getVehicleType().getTypeName() : "—");
                row.put("preferredProduct", v.getPreferredProduct() != null ? v.getPreferredProduct().getName() : "—");
                row.put("maxCapacity", formatNumber(v.getMaxCapacity()));
                row.put("monthlyLimit", formatNumber(v.getMaxLitersPerMonth()));
                row.put("consumed", formatNumber(v.getConsumedLiters()));

                String status = v.getStatus() != null ? v.getStatus() : "ACTIVE";
                row.put("status", status);

                switch (status) {
                    case "ACTIVE" -> active++;
                    case "INACTIVE" -> inactive++;
                    case "BLOCKED" -> blocked++;
                }

                if (v.getMaxCapacity() != null) totalCapacity = totalCapacity.add(v.getMaxCapacity());
                if (v.getMaxLitersPerMonth() != null) totalMonthly = totalMonthly.add(v.getMaxLitersPerMonth());
                if (v.getConsumedLiters() != null) totalConsumed = totalConsumed.add(v.getConsumedLiters());

                vehicleData.add(row);
            }

            params.put("totalVehicles", vehicles.size());
            params.put("activeCount", active);
            params.put("inactiveCount", inactive);
            params.put("blockedCount", blocked);
            params.put("totalCapacity", formatNumber(totalCapacity));
            params.put("totalMonthlyLimit", formatNumber(totalMonthly));
            params.put("totalConsumed", formatNumber(totalConsumed));

            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(vehicleData);
            JasperPrint print = JasperFillManager.fillReport(report, params, dataSource);

            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to generate Customer Vehicle Report", e);
        }
    }

    private JasperReport getCompiledReport(String templatePath) throws Exception {
        return compiledReports.computeIfAbsent(templatePath, path -> {
            try {
                InputStream is = new ClassPathResource(path).getInputStream();
                return JasperCompileManager.compileReport(is);
            } catch (Exception e) {
                throw new ReportGenerationException("Failed to compile report: " + path, e);
            }
        });
    }

    private String formatNumber(BigDecimal value) {
        if (value == null) return "0.00";
        return NUM_FMT.format(value);
    }
}
