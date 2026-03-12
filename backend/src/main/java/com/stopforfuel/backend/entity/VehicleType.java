package com.stopforfuel.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vehicle_type")
@Getter
@Setter
public class VehicleType extends SimpleBaseEntity {

    @Column(name = "type_name", nullable = false)
    private String typeName;

    private String description;
}
