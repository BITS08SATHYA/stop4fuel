package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Nozzle;
import com.stopforfuel.backend.entity.Pump;
import com.stopforfuel.backend.entity.Tank;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class NozzleDTO {
    private Long id;
    private String nozzleName;
    private String nozzleNumber;
    private String nozzleCompany;
    private LocalDate stampingExpiryDate;
    private boolean active;
    private TankSummary tank;
    private PumpSummary pump;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NozzleDTO from(Nozzle n) {
        return NozzleDTO.builder()
                .id(n.getId())
                .nozzleName(n.getNozzleName())
                .nozzleNumber(n.getNozzleNumber())
                .nozzleCompany(n.getNozzleCompany())
                .stampingExpiryDate(n.getStampingExpiryDate())
                .active(n.isActive())
                .tank(TankSummary.from(n.getTank()))
                .pump(PumpSummary.from(n.getPump()))
                .scid(n.getScid())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class TankSummary {
        private Long id;
        private String name;
        private String productName;

        public static TankSummary from(Tank t) {
            if (t == null) return null;
            return TankSummary.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .productName(t.getProduct() != null ? t.getProduct().getName() : null)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class PumpSummary {
        private Long id;
        private String name;

        public static PumpSummary from(Pump p) {
            if (p == null) return null;
            return PumpSummary.builder().id(p.getId()).name(p.getName()).build();
        }
    }
}
