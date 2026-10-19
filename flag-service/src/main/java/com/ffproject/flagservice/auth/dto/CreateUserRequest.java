package com.ffproject.flagservice.auth.dto;

import com.ffproject.flagservice.auth.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code POST /users} (platform admins only).
 */
@Getter
@Setter
public class CreateUserRequest {

    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(max = 128)
    private String password;

    @NotBlank
    private String tenantId;

    /** Defaults to {@link Role#TENANT_USER} when omitted. */
    private Role role;
}
