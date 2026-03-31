package com.stopforfuel.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRoleRequest {

    @NotBlank(message = "Role type is required")
    private String roleType;
}
