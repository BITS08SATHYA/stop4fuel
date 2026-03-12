package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
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

    @Column(name = "role_type", nullable = false, unique = true)
    private String roleType; // e.g., "CUSTOMER", "EMPLOYEE", "DEALER"
}
