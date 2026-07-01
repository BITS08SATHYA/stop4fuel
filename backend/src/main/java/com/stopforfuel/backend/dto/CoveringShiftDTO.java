package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.ShiftClosingReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * The shift whose window covers a given timestamp, together with its closing-report state —
 * used by the invoice "Change Date / Move" dialog to resolve the target shift for a corrected
 * date REGARDLESS of finalize status. Unlike the movable-shifts list (which hides FINALIZED
 * shifts), this surfaces a finalized covering shift so the admin can un-finalize it in place
 * and then move the bill.
 */
@Getter
@Builder
public class CoveringShiftDTO {
    private Long shiftId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String attendantName;
    /** Closing report id, or null when the shift has no report yet (e.g. still OPEN). */
    private Long reportId;
    /** Report status: DRAFT / FINALIZED, or null when no report exists. */
    private String reportStatus;

    public static CoveringShiftDTO from(Shift s, ShiftClosingReport report) {
        return CoveringShiftDTO.builder()
                .shiftId(s.getId())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .status(s.getStatus() != null ? s.getStatus().name() : null)
                .attendantName(s.getAttendant() != null ? s.getAttendant().getName() : null)
                .reportId(report != null ? report.getId() : null)
                .reportStatus(report != null ? report.getStatus() : null)
                .build();
    }
}
