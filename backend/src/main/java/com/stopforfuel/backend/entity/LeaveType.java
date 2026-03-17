package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
public class LeaveType extends SimpleBaseEntity {

    @Column(nullable = false)
    private String typeName;

    private Integer maxDaysPerYear;

    private Boolean carryForward = false;

    private Integer maxCarryForwardDays = 0;
}
