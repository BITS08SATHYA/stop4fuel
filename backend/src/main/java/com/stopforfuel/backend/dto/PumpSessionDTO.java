package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.PumpSession;
import com.stopforfuel.backend.entity.PumpSessionReading;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PumpSessionDTO {
    private Long id;
    private Long shiftId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private PumpSummary pump;
    private AttendantSummary attendant;
    private List<ReadingDTO> readings;
    private BigDecimal totalLiters;
    private BigDecimal totalSales;
    private Long scid;
    private LocalDateTime createdAt;

    public static PumpSessionDTO from(PumpSession ps) {
        BigDecimal totalLiters = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;

        List<ReadingDTO> readingDTOs = null;
        if (ps.getReadings() != null) {
            readingDTOs = ps.getReadings().stream().map(ReadingDTO::from).toList();
            for (ReadingDTO r : readingDTOs) {
                if (r.getLitersSold() != null) totalLiters = totalLiters.add(r.getLitersSold());
                if (r.getSalesAmount() != null) totalSales = totalSales.add(r.getSalesAmount());
            }
        }

        return PumpSessionDTO.builder()
                .id(ps.getId())
                .shiftId(ps.getShiftId())
                .status(ps.getStatus() != null ? ps.getStatus().name() : null)
                .startTime(ps.getStartTime())
                .endTime(ps.getEndTime())
                .pump(PumpSummary.from(ps.getPump()))
                .attendant(AttendantSummary.from(ps.getAttendant()))
                .readings(readingDTOs)
                .totalLiters(totalLiters)
                .totalSales(totalSales)
                .scid(ps.getScid())
                .createdAt(ps.getCreatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class PumpSummary {
        private Long id;
        private String name;

        public static PumpSummary from(com.stopforfuel.backend.entity.Pump p) {
            if (p == null) return null;
            return PumpSummary.builder()
                    .id(p.getId())
                    .name(p.getName())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class AttendantSummary {
        private Long id;
        private String name;
        private String username;

        public static AttendantSummary from(com.stopforfuel.backend.entity.User u) {
            if (u == null) return null;
            return AttendantSummary.builder()
                    .id(u.getId())
                    .name(u.getName())
                    .username(u.getUsername())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ReadingDTO {
        private Long id;
        private Long nozzleId;
        private String nozzleName;
        private String productName;
        private BigDecimal productPrice;
        private BigDecimal openReading;
        private BigDecimal closeReading;
        private BigDecimal litersSold;
        private BigDecimal salesAmount;

        public static ReadingDTO from(PumpSessionReading r) {
            String nozzleName = null;
            String productName = null;
            BigDecimal productPrice = null;
            if (r.getNozzle() != null) {
                nozzleName = r.getNozzle().getNozzleName();
                if (r.getNozzle().getTank() != null && r.getNozzle().getTank().getProduct() != null) {
                    productName = r.getNozzle().getTank().getProduct().getName();
                    productPrice = r.getNozzle().getTank().getProduct().getPrice();
                }
            }
            return ReadingDTO.builder()
                    .id(r.getId())
                    .nozzleId(r.getNozzle() != null ? r.getNozzle().getId() : null)
                    .nozzleName(nozzleName)
                    .productName(productName)
                    .productPrice(productPrice)
                    .openReading(r.getOpenReading())
                    .closeReading(r.getCloseReading())
                    .litersSold(r.getLitersSold())
                    .salesAmount(r.getSalesAmount())
                    .build();
        }
    }
}
