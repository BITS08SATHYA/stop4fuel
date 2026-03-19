package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Roles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Role type is required")
    @Column(name = "role_type", nullable = false, unique = true)
    private String roleType; // e.g., "CUSTOMER", "EMPLOYEE", "DEALER"
}
