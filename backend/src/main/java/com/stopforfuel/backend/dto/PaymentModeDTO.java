package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.PaymentMode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentModeDTO {
    private Long id;
    private String modeName;

    public static PaymentModeDTO from(PaymentMode pm) {
        return PaymentModeDTO.builder()
                .id(pm.getId())
                .modeName(pm.getModeName())
                .build();
    }
}
