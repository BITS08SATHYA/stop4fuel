package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ExtractionResult;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Supplier;
import com.stopforfuel.backend.repository.ProductRepository;
import com.stopforfuel.backend.repository.SupplierRepository;
import com.stopforfuel.backend.util.FileUploadValidator;
import com.stopforfuel.config.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class PurchaseInvoiceExtractionService {

    private static final String SYSTEM_PROMPT = """
            You are extracting fields from an Indian fuel-station purchase invoice (IOCL, BPCL,
            HPCL, or non-fuel supplier). Return JSON only, no commentary.

            For each line item, normalize:
            - quantityLitres to LITRES (multiply by 1000 if invoice unit is KL).
            - basicPricePerLitre to rupees per litre (divide by 1000 if rate is per KL).
            - additionalTaxAmount as the rupee total of any per-KL Additional VAT line
              (rate x qty), not the per-KL rate itself.
            - basicAmount, taxAmount, totalAmount as rupees for that line.
            - taxPercent as the percentage on the VAT/GST line (e.g. 13.0 for "13.000 %").

            Top-level fields:
            - invoiceNumber: the document/tax invoice number (e.g. "20274150B000997").
            - sapEntryNumber: the SAP Entry no printed in the header (e.g. "7005192190"),
              null if absent. This is separate from invoiceNumber.
            - roundingAdjustment: the rupee value on any "ZRND Rounding Difference" /
              "Rounding Off" line at the bottom of the invoice, signed (positive if it
              raises the total, negative if it lowers). Null if the invoice has no
              rounding line.
            - totalAmount: the printed grand total at the bottom of the invoice,
              including any rounding adjustment.

            Map invoiceType:
            - "FUEL" if any item's HSN starts with 2710.
            - "NON_FUEL" otherwise.

            Dates: convert dd-MMM-yy or dd/MM/yyyy to ISO yyyy-MM-dd. If only one date is
            present on the invoice, use it for both invoiceDate and deliveryDate.

            supplierName should be the issuer of the invoice (the "Supplier" block, e.g.
            "Indian Oil Corporation Limited"), not the consignee/dealer.
            supplierGstin should be the supplier's GSTIN/TIN if printed.

            If a field is genuinely absent from the invoice, return null. Do not invent values.
            """;

    private final ChatClient extractionChatClient;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public PurchaseInvoiceExtractionService(ChatClient.Builder chatClientBuilder,
                                            SupplierRepository supplierRepository,
                                            ProductRepository productRepository) {
        this.extractionChatClient = chatClientBuilder.build();
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    public ExtractionResult extract(MultipartFile file) throws IOException {
        FileUploadValidator.validatePdf(file);

        String rawText;
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            rawText = stripper.getText(doc);
        }
        if (rawText == null || rawText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "PDF appears to be a scan with no text layer; OCR is not yet supported");
        }

        ExtractedInvoice extracted;
        try {
            extracted = extractionChatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(rawText)
                    .call()
                    .entity(ExtractedInvoice.class);
        } catch (Exception e) {
            log.error("PDF extraction via Claude failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to extract invoice fields from PDF: " + e.getMessage());
        }
        if (extracted == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Extractor returned no structured data");
        }

        Long scid = SecurityUtils.getScid();
        ExtractionResult.SupplierMatch supplierMatch =
                resolveSupplier(extracted.supplierName(), extracted.supplierGstin(), scid);

        List<Product> productPool = loadProductPool(extracted.invoiceType(), scid);
        List<ExtractionResult.ItemMatch> itemMatches = extracted.items() == null
                ? List.of()
                : extracted.items().stream()
                        .map(it -> resolveItem(it, productPool))
                        .toList();

        return new ExtractionResult(
                supplierMatch,
                extracted.invoiceNumber(),
                extracted.sapEntryNumber(),
                parseDate(extracted.invoiceDate()),
                parseDate(extracted.deliveryDate()),
                normalizeInvoiceType(extracted.invoiceType()),
                extracted.totalAmount(),
                extracted.roundingAdjustment(),
                extracted.remarks(),
                itemMatches
        );
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            log.warn("Could not parse extracted date '{}'", s);
            return null;
        }
    }

    private ExtractionResult.SupplierMatch resolveSupplier(String name, String gstin, Long scid) {
        List<Supplier> suppliers = supplierRepository.findAllByScid(scid);
        String trimmedName = name == null ? null : name.trim();
        String trimmedGstin = gstin == null ? null : gstin.trim();

        if (trimmedGstin != null && !trimmedGstin.isEmpty()) {
            for (Supplier s : suppliers) {
                if (s.getGstNumber() != null
                        && s.getGstNumber().trim().equalsIgnoreCase(trimmedGstin)) {
                    return new ExtractionResult.SupplierMatch(
                            s.getId(), s.getName(), trimmedName, trimmedGstin, "GSTIN");
                }
            }
        }

        if (trimmedName != null && !trimmedName.isEmpty()) {
            Set<String> extractedTokens = tokenize(trimmedName);
            Supplier best = null;
            int bestOverlap = 0;
            for (Supplier s : suppliers) {
                if (s.getName() == null) continue;
                Set<String> supplierTokens = tokenize(s.getName());
                int overlap = countOverlap(extractedTokens, supplierTokens);
                if (overlap > bestOverlap) {
                    bestOverlap = overlap;
                    best = s;
                }
            }
            if (best != null && bestOverlap >= 2) {
                return new ExtractionResult.SupplierMatch(
                        best.getId(), best.getName(), trimmedName, trimmedGstin, "NAME");
            }
        }

        return new ExtractionResult.SupplierMatch(null, null, trimmedName, trimmedGstin, null);
    }

    private List<Product> loadProductPool(String invoiceType, Long scid) {
        if ("FUEL".equalsIgnoreCase(invoiceType)) {
            return productRepository.findByCategoryIgnoreCaseAndActiveAndScid("FUEL", true, scid);
        }
        if ("NON_FUEL".equalsIgnoreCase(invoiceType)) {
            return productRepository.findByCategoryNotIgnoreCaseAndActiveAndScid("FUEL", true, scid);
        }
        return productRepository.findByActiveAndScid(true, scid);
    }

    private ExtractionResult.ItemMatch resolveItem(ExtractedInvoice.ExtractedItem item,
                                                    List<Product> productPool) {
        String description = item.description();
        String hsn = item.hsnCode();
        String hsnDigits = digitsOnly(hsn);

        Product matched = null;
        String reason = null;

        if (hsnDigits != null && !hsnDigits.isEmpty()) {
            for (Product p : productPool) {
                if (p.getHsnCode() != null
                        && digitsOnly(p.getHsnCode()).equals(hsnDigits)) {
                    matched = p;
                    reason = "HSN";
                    break;
                }
            }
        }

        if (matched == null && description != null && !description.isBlank()) {
            String descLower = description.toLowerCase();
            for (Product p : productPool) {
                if (p.getName() == null) continue;
                String nameLower = p.getName().toLowerCase();
                if (nameLower.contains(descLower) || descLower.contains(nameLower)) {
                    matched = p;
                    reason = "NAME";
                    break;
                }
            }
        }

        return new ExtractionResult.ItemMatch(
                matched != null ? matched.getId() : null,
                matched != null ? matched.getName() : null,
                description,
                hsn,
                reason,
                item.quantityLitres(),
                item.basicPricePerLitre(),
                item.basicAmount(),
                item.taxPercent(),
                item.taxAmount(),
                item.additionalTaxAmount(),
                item.totalAmount()
        );
    }

    private static String digitsOnly(String s) {
        return s == null ? null : s.replaceAll("\\D", "");
    }

    private static Set<String> tokenize(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        for (String tok : s.toLowerCase().split("[^a-z0-9]+")) {
            if (tok.length() >= 3) out.add(tok);
        }
        out.removeAll(Arrays.asList("ltd", "limited", "pvt", "private", "the", "and", "company", "corp", "corporation"));
        return out;
    }

    private static int countOverlap(Set<String> a, Set<String> b) {
        int n = 0;
        for (String t : a) if (b.contains(t)) n++;
        return n;
    }

    private static String normalizeInvoiceType(String t) {
        if (t == null) return null;
        String u = t.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return ("FUEL".equals(u) || "NON_FUEL".equals(u)) ? u : null;
    }

    public record ExtractedInvoice(
            String supplierName,
            String supplierGstin,
            String invoiceNumber,
            String sapEntryNumber,
            String invoiceDate,
            String deliveryDate,
            String invoiceType,
            BigDecimal totalAmount,
            BigDecimal roundingAdjustment,
            String remarks,
            List<ExtractedItem> items
    ) {
        public record ExtractedItem(
                String description,
                String hsnCode,
                BigDecimal quantityLitres,
                BigDecimal basicPricePerLitre,
                BigDecimal basicAmount,
                BigDecimal taxPercent,
                BigDecimal taxAmount,
                BigDecimal additionalTaxAmount,
                BigDecimal totalAmount
        ) {}
    }
}
