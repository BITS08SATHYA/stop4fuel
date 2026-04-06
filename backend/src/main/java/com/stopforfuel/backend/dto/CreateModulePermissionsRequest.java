package com.stopforfuel.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateModulePermissionsRequest {

    @NotBlank(message = "Module name is required")
    @Pattern(regexp = "^[A-Z][A-Z_]*$", message = "Module must be uppercase letters and underscores")
    private String module;

    private String description;
}
