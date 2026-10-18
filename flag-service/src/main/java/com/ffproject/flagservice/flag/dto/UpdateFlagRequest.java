package com.ffproject.flagservice.flag.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code PATCH /flags/{name}}.
 *
 * <p>Only the value is mutable; a flag's name is its identity and cannot be
 * changed (Requirement 1.4).</p>
 */
@Getter
@Setter
public class UpdateFlagRequest {

    @NotNull
    private Boolean value;
}
