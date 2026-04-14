package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.InvoiceBill;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class OutstandingBillDTO {
    private Long id;
    private String billNo;
    private LocalDateTime date;
    private Long customerId;
    private String customerName;
    private Long vehicleId;
    private String vehicleNumber;
    private BigDecimal netAmount;
    private BigDecimal paidAmount;
    private BigDecimal balance;
    private String paymentStatus;

    public static OutstandingBillDTO from(InvoiceBill b, BigDecimal paid) {
        BigDecimal net = b.getNetAmount() != null ? b.getNetAmount() : BigDecimal.ZERO;
        BigDecimal p = paid != null ? paid : BigDecimal.ZERO;
        return OutstandingBillDTO.builder()
                .id(b.getId())
                .billNo(b.getBillNo())
                .date(b.getDate())
                .customerId(b.getCustomer() != null ? b.getCustomer().getId() : null)
                .customerName(b.getCustomer() != null ? b.getCustomer().getName() : null)
                .vehicleId(b.getVehicle() != null ? b.getVehicle().getId() : null)
                .vehicleNumber(b.getVehicle() != null ? b.getVehicle().getVehicleNumber() : null)
                .netAmount(net)
                .paidAmount(p)
                .balance(net.subtract(p))
                .paymentStatus(b.getPaymentStatus() != null ? b.getPaymentStatus().name() : null)
                .build();
    }
}
