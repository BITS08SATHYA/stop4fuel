package com.stopforfuel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oil_type")
@Getter
@Setter
public class OilType extends SimpleBaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
