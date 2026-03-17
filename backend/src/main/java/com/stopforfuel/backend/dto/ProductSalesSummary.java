package com.stopforfuel.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ProductSalesSummary {
    private Long productId;
    private String productName;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private BigDecimal totalGrossAmount;
    private BigDecimal totalDiscount;
}
