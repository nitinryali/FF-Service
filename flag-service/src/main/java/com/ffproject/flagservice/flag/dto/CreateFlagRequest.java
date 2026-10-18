package com.ffproject.flagservice.flag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code POST /flags}.
 *
 * <p>The {@code name} constraint is the single source of truth for the flag
 * naming rule: 1–100 characters drawn from {@code [a-zA-Z0-9-_]}
 * (Requirements 1.1, 1.9).</p>
 */
@Getter
@Setter
public class CreateFlagRequest {

    @NotBlank
    @Pattern(
            regexp = "^[a-zA-Z0-9\\-_]{1,100}$",
            message = "Flag name must be 1–100 characters: alphanumeric, hyphens, underscores only")
    private String name;

    @NotNull
    private Boolean value;
}
