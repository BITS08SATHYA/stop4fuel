package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "pump")
@Getter
@Setter
public class Pump extends BaseEntity {

    @NotBlank(message = "Pump name is required")
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;
}
