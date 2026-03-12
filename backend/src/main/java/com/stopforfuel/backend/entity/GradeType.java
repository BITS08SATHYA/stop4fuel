package com.stopforfuel.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grade_type")
@Getter
@Setter
public class GradeType extends BaseEntity {

    /**
     * The fluid/oil type category, e.g., "Engine Oil", "Gear Oil", "Diesel", "Petrol"
     */
    @Column(name = "oil_type")
    private String oilType;

    /**
     * The specific variant/grade name, e.g., "20W-40", "20W-50", "Premium 95"
     */
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
