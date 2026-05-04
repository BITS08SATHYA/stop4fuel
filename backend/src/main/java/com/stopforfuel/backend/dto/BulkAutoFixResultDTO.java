package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkAutoFixResultDTO {
    private int total;
    private int fixed;
    private int needsManual;
    private int waitingForStatement;
    private List<AutoFixResultDTO> results;
}
