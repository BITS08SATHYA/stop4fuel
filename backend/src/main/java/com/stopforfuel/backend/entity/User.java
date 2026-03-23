package com.stopforfuel.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_status", columnList = "status")
})
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
public class User extends PersonEntity {

    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username must not exceed 100 characters")
    @Column(nullable = false, unique = true)
    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password")
    private String password;

    @Column(name = "cognito_id", unique = true)
    private String cognitoId; // The "sub" from Cognito

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Roles role;

    @Column(name = "join_date")
    @PastOrPresent(message = "Join date cannot be in the future")
    private LocalDate joinDate;

    @Pattern(regexp = "^(Active|Inactive|ACTIVE|INACTIVE)$", message = "Status must be Active or Inactive")
    private String status;
}
