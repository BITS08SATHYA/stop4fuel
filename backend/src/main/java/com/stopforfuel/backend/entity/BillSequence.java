package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bill_sequence", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"type", "fy_year"})
})
@Getter
@Setter
public class BillSequence extends SimpleBaseEntity {

    @Column(name = "type", nullable = false)
    private String type; // CASH, CREDIT, STMT

    @Column(name = "fy_year", nullable = false)
    private Integer fyYear; // 26 = FY 2026-27

    @Column(name = "last_number", nullable = false)
    private Long lastNumber = 0L;
}
