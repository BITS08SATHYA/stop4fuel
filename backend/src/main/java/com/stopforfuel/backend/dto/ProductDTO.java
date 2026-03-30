package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.GradeType;
import com.stopforfuel.backend.entity.OilType;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Supplier;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ProductDTO {
    private Long id;
    private String name;
    private String hsnCode;
    private BigDecimal price;
    private String category;
    private String unit;
    private Double volume;
    private String brand;
    private BigDecimal gstRate;
    private String fuelFamily;
    private BigDecimal discountRate;
    private boolean active;
    private SupplierSummary supplier;
    private OilTypeSummary oilType;
    private GradeTypeSummary gradeType;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductDTO from(Product p) {
        return ProductDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .hsnCode(p.getHsnCode())
                .price(p.getPrice())
                .category(p.getCategory())
                .unit(p.getUnit())
                .volume(p.getVolume())
                .brand(p.getBrand())
                .gstRate(p.getGstRate())
                .fuelFamily(p.getFuelFamily())
                .discountRate(p.getDiscountRate())
                .active(p.isActive())
                .supplier(SupplierSummary.from(p.getSupplier()))
                .oilType(OilTypeSummary.from(p.getOilType()))
                .gradeType(GradeTypeSummary.from(p.getGrade()))
                .scid(p.getScid())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class SupplierSummary {
        private Long id;
        private String name;

        public static SupplierSummary from(Supplier s) {
            if (s == null) return null;
            return SupplierSummary.builder().id(s.getId()).name(s.getName()).build();
        }
    }

    @Getter
    @Builder
    public static class OilTypeSummary {
        private Long id;
        private String name;

        public static OilTypeSummary from(OilType o) {
            if (o == null) return null;
            return OilTypeSummary.builder().id(o.getId()).name(o.getName()).build();
        }
    }

    @Getter
    @Builder
    public static class GradeTypeSummary {
        private Long id;
        private String name;

        public static GradeTypeSummary from(GradeType g) {
            if (g == null) return null;
            return GradeTypeSummary.builder().id(g.getId()).name(g.getName()).build();
        }
    }
}
