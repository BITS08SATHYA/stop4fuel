package com.stopforfuel.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ShiftClosingDataDTO {

    private Long shiftId;
    private String shiftStatus;
    private LocalDateTime startTime;
    private String attendantName;

    private List<NozzleReadingRow> nozzleReadings;
    private List<TankDipRow> tankDips;

    // Pre-computed read-only totals from existing shift data
    private BigDecimal billPaymentTotal;
    private BigDecimal statementPaymentTotal;
    private BigDecimal externalInflowTotal;
    private BigDecimal creditBillTotal;
    private Map<String, BigDecimal> eAdvanceTotals;
    private Map<String, BigDecimal> opAdvanceTotals;
    private BigDecimal expenseTotal;
    private BigDecimal incentiveTotal;
    private BigDecimal inflowRepaymentTotal;

    @Getter
    @Setter
    public static class NozzleReadingRow {
        private Long nozzleId;
        private String nozzleName;
        private String pumpName;
        private String productName;
        private Double productPrice;
        private Double openMeterReading;
    }

    @Getter
    @Setter
    public static class TankDipRow {
        private Long tankId;
        private String tankName;
        private String productName;
        private Double capacity;
        private String openDip;
        private Double openStock;
    }
}
