package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.*;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class InvoiceBillDTO {
    private Long id;
    private LocalDateTime date;
    private String billDesc;
    private String pumpBillPic;
    private String billPic;
    private String signatoryName;
    private String signatoryCellNo;
    private Long vehicleKM;
    private Long readingOpen;
    private Long readingClose;
    private String customerGST;
    private BigDecimal grossAmount;
    private BigDecimal totalDiscount;
    private BigDecimal netAmount;
    private String billNo;
    private String billType;
    private String paymentMode;
    private String indentNo;
    private String indentPic;
    private String status;
    private String driverName;
    private String driverPhone;
    private String paymentStatus;
    private Long shiftId;
    private CustomerSummary customer;
    private VehicleSummary vehicle;
    private UserSummary raisedBy;
    private StatementSummary statement;
    private List<InvoiceProductDTO> products;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InvoiceBillDTO from(InvoiceBill b) {
        return InvoiceBillDTO.builder()
                .id(b.getId())
                .date(b.getDate())
                .billDesc(b.getBillDesc())
                .pumpBillPic(b.getPumpBillPic())
                .billPic(b.getBillPic())
                .signatoryName(b.getSignatoryName())
                .signatoryCellNo(b.getSignatoryCellNo())
                .vehicleKM(b.getVehicleKM())
                .readingOpen(b.getReadingOpen())
                .readingClose(b.getReadingClose())
                .customerGST(b.getCustomerGST())
                .grossAmount(b.getGrossAmount())
                .totalDiscount(b.getTotalDiscount())
                .netAmount(b.getNetAmount())
                .billNo(b.getBillNo())
                .billType(b.getBillType())
                .paymentMode(b.getPaymentMode())
                .indentNo(b.getIndentNo())
                .indentPic(b.getIndentPic())
                .status(b.getStatus())
                .driverName(b.getDriverName())
                .driverPhone(b.getDriverPhone())
                .paymentStatus(b.getPaymentStatus())
                .shiftId(b.getShiftId())
                .customer(CustomerSummary.from(b.getCustomer()))
                .vehicle(VehicleSummary.from(b.getVehicle()))
                .raisedBy(UserSummary.from(b.getRaisedBy()))
                .statement(StatementSummary.from(b.getStatement()))
                .products(b.getProducts() != null
                        ? b.getProducts().stream().map(InvoiceProductDTO::from).collect(Collectors.toList())
                        : null)
                .scid(b.getScid())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class CustomerSummary {
        private Long id;
        private String name;
        private String username;

        public static CustomerSummary from(Customer c) {
            if (c == null) return null;
            return CustomerSummary.builder().id(c.getId()).name(c.getName()).username(c.getUsername()).build();
        }
    }

    @Getter
    @Builder
    public static class VehicleSummary {
        private Long id;
        private String vehicleNumber;

        public static VehicleSummary from(Vehicle v) {
            if (v == null) return null;
            return VehicleSummary.builder().id(v.getId()).vehicleNumber(v.getVehicleNumber()).build();
        }
    }

    @Getter
    @Builder
    public static class UserSummary {
        private Long id;
        private String name;
        private String username;

        public static UserSummary from(User u) {
            if (u == null) return null;
            return UserSummary.builder().id(u.getId()).name(u.getName()).username(u.getUsername()).build();
        }
    }

    @Getter
    @Builder
    public static class StatementSummary {
        private Long id;
        private String statementNo;

        public static StatementSummary from(Statement s) {
            if (s == null) return null;
            return StatementSummary.builder().id(s.getId()).statementNo(s.getStatementNo()).build();
        }
    }

    @Getter
    @Builder
    public static class InvoiceProductDTO {
        private Long id;
        private Long productId;
        private String productName;
        private Long nozzleId;
        private String nozzleName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private BigDecimal grossAmount;
        private BigDecimal discountRate;
        private BigDecimal discountAmount;

        public static InvoiceProductDTO from(InvoiceProduct ip) {
            if (ip == null) return null;
            return InvoiceProductDTO.builder()
                    .id(ip.getId())
                    .productId(ip.getProduct() != null ? ip.getProduct().getId() : null)
                    .productName(ip.getProduct() != null ? ip.getProduct().getName() : null)
                    .nozzleId(ip.getNozzle() != null ? ip.getNozzle().getId() : null)
                    .nozzleName(ip.getNozzle() != null ? ip.getNozzle().getNozzleName() : null)
                    .quantity(ip.getQuantity())
                    .unitPrice(ip.getUnitPrice())
                    .amount(ip.getAmount())
                    .grossAmount(ip.getGrossAmount())
                    .discountRate(ip.getDiscountRate())
                    .discountAmount(ip.getDiscountAmount())
                    .build();
        }
    }
}
