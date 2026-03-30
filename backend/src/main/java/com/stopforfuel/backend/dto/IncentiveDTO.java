package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Incentive;
import com.stopforfuel.backend.entity.Product;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class IncentiveDTO {
    private Long id;
    private BigDecimal minQuantity;
    private BigDecimal discountRate;
    private boolean active;
    private CustomerSummary customer;
    private ProductSummary product;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static IncentiveDTO from(Incentive i) {
        return IncentiveDTO.builder()
                .id(i.getId())
                .minQuantity(i.getMinQuantity())
                .discountRate(i.getDiscountRate())
                .active(i.isActive())
                .customer(CustomerSummary.from(i.getCustomer()))
                .product(ProductSummary.from(i.getProduct()))
                .scid(i.getScid())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class CustomerSummary {
        private Long id;
        private String name;

        public static CustomerSummary from(Customer c) {
            if (c == null) return null;
            return CustomerSummary.builder().id(c.getId()).name(c.getName()).build();
        }
    }

    @Getter
    @Builder
    public static class ProductSummary {
        private Long id;
        private String name;

        public static ProductSummary from(Product p) {
            if (p == null) return null;
            return ProductSummary.builder().id(p.getId()).name(p.getName()).build();
        }
    }
}
