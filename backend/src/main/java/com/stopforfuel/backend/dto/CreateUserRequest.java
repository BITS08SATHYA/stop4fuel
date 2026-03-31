package com.stopforfuel.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String phone;

    @NotBlank(message = "Role type is required")
    private String roleType;

    private String designation;
    private String userType;
    private String username;

    @Email(message = "Invalid email format")
    private String email;

    private String tempPassword;
}
