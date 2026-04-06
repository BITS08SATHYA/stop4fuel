package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permissions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_permissions_code", columnNames = {"code"})
})
@Getter
@Setter
@NoArgsConstructor
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

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private boolean systemDefault = true;

    public Permission(String code, String description, String module, String action, boolean systemDefault) {
        this.code = code;
        this.description = description;
        this.module = module;
        this.action = action;
        this.systemDefault = systemDefault;
    }
}
