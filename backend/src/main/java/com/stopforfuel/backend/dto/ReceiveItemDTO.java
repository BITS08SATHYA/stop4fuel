package com.stopforfuel.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReceiveItemDTO {
    private Long itemId;
    private Double receivedQty;
}
