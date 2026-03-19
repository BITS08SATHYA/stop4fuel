package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
public class LeaveType extends SimpleBaseEntity {

    @jakarta.validation.constraints.NotBlank(message = "Leave type name is required")
    @Column(nullable = false)
    private String typeName;

    @jakarta.validation.constraints.PositiveOrZero(message = "Max days per year must be zero or positive")
    private Integer maxDaysPerYear;

    private Boolean carryForward = false;

    private Integer maxCarryForwardDays = 0;
}
