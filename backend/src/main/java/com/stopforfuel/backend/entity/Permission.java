package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_permissions_code", columnNames = {"code"})
})
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String code;

    private String description;

    @Column(nullable = false)
    private String module;
}
