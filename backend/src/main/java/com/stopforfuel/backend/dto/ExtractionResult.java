package com.stopforfuel.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExtractionResult(
        SupplierMatch supplier,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate deliveryDate,
        String invoiceType,
        BigDecimal totalAmount,
        String remarks,
        List<ItemMatch> items
) {
    public record SupplierMatch(
            Long matchedId,
            String matchedName,
            String extractedName,
            String extractedGstin,
            String matchReason
    ) {}

    public record ItemMatch(
            Long matchedProductId,
            String matchedProductName,
            String extractedDescription,
            String extractedHsn,
            String matchReason,
            BigDecimal quantityLitres,
            BigDecimal basicPricePerLitre,
            BigDecimal basicAmount,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            BigDecimal additionalTaxAmount,
            BigDecimal totalAmount
    ) {}
}
