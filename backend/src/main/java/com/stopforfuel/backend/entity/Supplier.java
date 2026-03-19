package com.stopforfuel.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "supplier")
@Getter
@Setter
public class Supplier extends BaseEntity {

    @NotBlank(message = "Supplier name is required")
    @Size(max = 255, message = "Supplier name must not exceed 255 characters")
    @Column(nullable = false)
    private String name;

    private String contactPerson;

    @Size(max = 15, message = "Phone number must not exceed 15 characters")
    private String phone;

    @Email(message = "Invalid email format")
    private String email;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
