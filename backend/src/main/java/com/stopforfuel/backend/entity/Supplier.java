package com.stopforfuel.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "supplier")
@Getter
@Setter
public class Supplier extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String contactPerson;

    private String phone;

    private String email;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
