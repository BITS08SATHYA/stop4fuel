package com.stopforfuel.backend.dto;

import com.stopforfuel.backend.entity.Shift;
import com.stopforfuel.backend.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ShiftDTO {
    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private AttendantSummary attendant;
    private Long scid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ShiftDTO from(Shift s) {
        return ShiftDTO.builder()
                .id(s.getId())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .status(s.getStatus() != null ? s.getStatus().name() : null)
                .attendant(AttendantSummary.from(s.getAttendant()))
                .scid(s.getScid())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    @Getter
    @Builder
    public static class AttendantSummary {
        private Long id;
        private String name;
        private String username;

        public static AttendantSummary from(User u) {
            if (u == null) return null;
            return AttendantSummary.builder()
                    .id(u.getId())
                    .name(u.getName())
                    .username(u.getUsername())
                    .build();
        }
    }
}
