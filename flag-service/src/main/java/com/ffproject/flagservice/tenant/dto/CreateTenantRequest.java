package com.ffproject.flagservice.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code POST /tenants}.
 */
@Getter
@Setter
public class CreateTenantRequest {

    @NotBlank
    @Pattern(
            regexp = "^[a-zA-Z0-9\\-_]{1,100}$",
            message = "Tenant name must be 1–100 characters: alphanumeric, hyphens, underscores only")
    private String name;
}
