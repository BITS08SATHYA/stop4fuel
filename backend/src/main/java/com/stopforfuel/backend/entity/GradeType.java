package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grade_type")
@Getter
@Setter
public class GradeType extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "oil_type_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private OilType oilType;

    /**
     * The specific variant/grade name, e.g., "20W-40", "20W-50", "Premium 95"
     */
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
