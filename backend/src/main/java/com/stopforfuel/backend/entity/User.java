package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class User extends PersonEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "cognito_id", unique = true)
    private String cognitoId; // The "sub" from Cognito

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Roles role;

    @Column(name = "join_date")
    private LocalDate joinDate;

    private String status;
}
