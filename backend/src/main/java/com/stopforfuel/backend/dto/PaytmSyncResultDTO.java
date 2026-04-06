package com.stopforfuel.backend.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class PaytmSyncResultDTO {
    private int fetched;
    private int newlyStored;
    private int skippedDuplicates;
    private int matched;
    private LocalDate syncDate;
}
