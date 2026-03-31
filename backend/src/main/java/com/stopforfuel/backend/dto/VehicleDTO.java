package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Customer;
import com.stopforfuel.backend.entity.Product;
import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.entity.VehicleType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class VehicleDTO {
    private Long id;
    private String vehicleNumber;
    private String status;
    private boolean isActive;
    private BigDecimal maxCapacity;
    private BigDecimal maxLitersPerMonth;
    private BigDecimal consumedLiters;
    private VehicleTypeSummary vehicleType;
    private ProductSummary preferredProduct;
    private CustomerSummary customer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VehicleDTO from(Vehicle v) {
        return VehicleDTO.builder()
                .id(v.getId())
                .vehicleNumber(v.getVehicleNumber())
                .status(v.getStatus() != null ? v.getStatus().name() : null)
                .isActive(v.isActive())
                .maxCapacity(v.getMaxCapacity())
                .maxLitersPerMonth(v.getMaxLitersPerMonth())
                .consumedLiters(v.getConsumedLiters())
                .vehicleType(VehicleTypeSummary.from(v.getVehicleType()))
                .preferredProduct(ProductSummary.from(v.getPreferredProduct()))
                .customer(CustomerSummary.from(v.getCustomer()))
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class VehicleTypeSummary {
        private Long id;
        private String name;

        public static VehicleTypeSummary from(VehicleType vt) {
            if (vt == null) return null;
            return VehicleTypeSummary.builder().id(vt.getId()).name(vt.getTypeName()).build();
        }
    }

    @Getter
    @Builder
    public static class ProductSummary {
        private Long id;
        private String name;
        private String fuelFamily;

        public static ProductSummary from(Product p) {
            if (p == null) return null;
            return ProductSummary.builder().id(p.getId()).name(p.getName()).fuelFamily(p.getFuelFamily()).build();
        }
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
}
