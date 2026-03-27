package com.stopforfuel.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ShiftClosingSubmitDTO {

    @NotNull(message = "Nozzle readings are required")
    private List<NozzleReadingInput> nozzleReadings;

    @NotNull(message = "Tank dip readings are required")
    private List<TankDipInput> tankDips;

    @Getter
    @Setter
    public static class NozzleReadingInput {
        @NotNull
        private Long nozzleId;
        private Double openMeterReading;
        @NotNull(message = "Close meter reading is required")
        private Double closeMeterReading;
        private Double testQuantity;
    }

    @Getter
    @Setter
    public static class TankDipInput {
        @NotNull
        private Long tankId;
        private String openDip;
        private Double openStock;
        private Double incomeStock;
        private String closeDip;
        @NotNull(message = "Close stock is required")
        private Double closeStock;
    }
}
