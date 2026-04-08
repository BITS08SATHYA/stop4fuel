package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.UtilityBill;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ReportGenerationException;
import com.stopforfuel.backend.repository.UtilityBillRepository;
import com.stopforfuel.config.SecurityUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UtilityBillService {

    private final UtilityBillRepository utilityBillRepository;

    @Transactional(readOnly = true)
    public List<UtilityBill> getAllBills() {
        return utilityBillRepository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<UtilityBill> getBillsByType(String type) {
        return utilityBillRepository.findByBillTypeAndScidOrderByBillDateDesc(type, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public List<UtilityBill> getPendingBills() {
        return utilityBillRepository.findByStatusAndScidOrderByDueDateAsc("PENDING", SecurityUtils.getScid());
    }

    public UtilityBill createBill(UtilityBill bill) {
        return utilityBillRepository.save(bill);
    }

    public UtilityBill updateBill(Long id, UtilityBill details) {
        UtilityBill bill = utilityBillRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Bill not found"));

        bill.setBillType(details.getBillType());
        bill.setProvider(details.getProvider());
        bill.setConsumerNumber(details.getConsumerNumber());
        bill.setBillDate(details.getBillDate());
        bill.setDueDate(details.getDueDate());
        bill.setBillAmount(details.getBillAmount());
        bill.setPaidAmount(details.getPaidAmount());
        bill.setStatus(details.getStatus());
        bill.setUnitsConsumed(details.getUnitsConsumed());
        bill.setBillPeriod(details.getBillPeriod());
        bill.setRemarks(details.getRemarks());

        return utilityBillRepository.save(bill);
    }

    public void deleteBill(Long id) {
        utilityBillRepository.deleteById(id);
    }

    public UtilityBill parseTnebPdf(MultipartFile file) {
        com.stopforfuel.backend.util.FileUploadValidator.validatePdf(file);
        try {
            PDDocument document = Loader.loadPDF(file.getBytes());
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            UtilityBill bill = new UtilityBill();
            bill.setBillType("ELECTRICITY");
            bill.setProvider("TNEB");

            // Parse consumer number
            Pattern consumerPattern = Pattern.compile("(?:Consumer|Service)\\s*(?:No|Number)[.:\\s]*([\\d\\s/-]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = consumerPattern.matcher(text);
            if (m.find()) {
                bill.setConsumerNumber(m.group(1).trim());
            }

            // Parse bill amount
            Pattern amountPattern = Pattern.compile("(?:Total|Net|Amount|Payable)[^\\d]*(?:Rs\\.?|₹)?\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE);
            m = amountPattern.matcher(text);
            if (m.find()) {
                bill.setBillAmount(Double.parseDouble(m.group(1).replace(",", "")));
            }

            // Parse units consumed
            Pattern unitsPattern = Pattern.compile("(?:Units?|Consumption)[^\\d]*([\\d,]+\\.?\\d*)\\s*(?:units|kWh)?", Pattern.CASE_INSENSITIVE);
            m = unitsPattern.matcher(text);
            if (m.find()) {
                bill.setUnitsConsumed(Double.parseDouble(m.group(1).replace(",", "")));
            }

            // Parse due date
            Pattern dueDatePattern = Pattern.compile("(?:Due|Pay\\s*by)[^\\d]*([\\d]{1,2}[./-][\\d]{1,2}[./-][\\d]{2,4})", Pattern.CASE_INSENSITIVE);
            m = dueDatePattern.matcher(text);
            if (m.find()) {
                String dateStr = m.group(1);
                try {
                    bill.setDueDate(parseDate(dateStr));
                } catch (Exception ignored) {}
            }

            // Parse bill date
            Pattern billDatePattern = Pattern.compile("(?:Bill|Invoice)\\s*Date[^\\d]*([\\d]{1,2}[./-][\\d]{1,2}[./-][\\d]{2,4})", Pattern.CASE_INSENSITIVE);
            m = billDatePattern.matcher(text);
            if (m.find()) {
                try {
                    bill.setBillDate(parseDate(m.group(1)));
                } catch (Exception ignored) {}
            }

            if (bill.getBillDate() == null) {
                bill.setBillDate(LocalDate.now());
            }

            bill.setStatus("PENDING");
            bill.setPaidAmount(0.0);

            return bill;
        } catch (Exception e) {
            throw new ReportGenerationException("Failed to parse PDF: " + e.getMessage(), e);
        }
    }

    public List<UtilityBill> parseBulkPdfs(List<MultipartFile> files) {
        List<UtilityBill> bills = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                UtilityBill bill = parseTnebPdf(file);
                bills.add(bill);
            } catch (Exception ignored) {}
        }
        return bills;
    }

    private LocalDate parseDate(String dateStr) {
        String[] formats = {"dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "dd/MM/yy", "dd-MM-yy"};
        for (String format : formats) {
            try {
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(format));
            } catch (Exception ignored) {}
        }
        throw new BusinessException("Unable to parse date: " + dateStr);
    }
}
