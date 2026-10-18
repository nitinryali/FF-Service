package com.ffproject.flagservice.flag;

import com.ffproject.flagservice.flag.dto.CreateFlagRequest;
import com.ffproject.flagservice.flag.dto.FlagResponse;
import com.ffproject.flagservice.flag.dto.UpdateFlagRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD endpoints for feature flags.
 *
 * <p>All routes require a valid JWT; authentication and rejection happen in
 * {@code JwtFilter} before any method here is invoked (Requirement 1.6).</p>
 *
 * <p>There is deliberately no exception handling in this class — every failure
 * is translated into an HTTP status by
 * {@link com.ffproject.flagservice.exception.GlobalExceptionHandler}
 * (Requirements 1.1–1.10).</p>
 */
@RestController
@RequestMapping("/flags")
@Tag(name = "Flags")
@SecurityRequirement(name = "bearerAuth")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    /**
     * Creates a flag.
     *
     * @param request the validated create payload
     * @return {@code 201} with the created flag
     */
    @PostMapping
    @Operation(summary = "Create flag")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Flag created"),
            @ApiResponse(responseCode = "400", description = "Invalid flag name"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "409", description = "Flag already exists"),
            @ApiResponse(responseCode = "503", description = "Database unavailable")
    })
    public ResponseEntity<FlagResponse> createFlag(@Valid @RequestBody CreateFlagRequest request) {
        Flag created = flagService.createFlag(request.getName(), request.getValue());
        return ResponseEntity.status(HttpStatus.CREATED).body(FlagResponse.from(created));
    }

    /**
     * Lists every flag.
     *
     * @return {@code 200} with all flags, or an empty array
     */
    @GetMapping
    @Operation(summary = "List all flags")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of flags returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<List<FlagResponse>> getAllFlags() {
        List<FlagResponse> flags = flagService.getAllFlags().stream()
                .map(FlagResponse::from)
                .toList();
        return ResponseEntity.ok(flags);
    }

    /**
     * Fetches one flag by name.
     *
     * @param name the flag name
     * @return {@code 200} with the flag, or {@code 404} if absent
     */
    @GetMapping("/{name}")
    @Operation(summary = "Get flag by name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Flag not found")
    })
    public ResponseEntity<FlagResponse> getFlagByName(
            @Parameter(description = "Flag name") @PathVariable String name) {
        return ResponseEntity.ok(FlagResponse.from(flagService.getFlagByName(name)));
    }

    /**
     * Updates a flag's boolean value.
     *
     * @param name    the flag name
     * @param request the validated update payload
     * @return {@code 200} with the updated flag, or {@code 404} if absent
     */
    @PatchMapping("/{name}")
    @Operation(summary = "Update flag value")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag updated"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Flag not found"),
            @ApiResponse(responseCode = "503", description = "Database unavailable")
    })
    public ResponseEntity<FlagResponse> updateFlag(
            @Parameter(description = "Flag name") @PathVariable String name,
            @Valid @RequestBody UpdateFlagRequest request) {
        Flag updated = flagService.updateFlag(name, request.getValue());
        return ResponseEntity.ok(FlagResponse.from(updated));
    }

    /**
     * Deletes a flag.
     *
     * @param name the flag name
     * @return {@code 204} with no body, or {@code 404} if absent
     */
    @DeleteMapping("/{name}")
    @Operation(summary = "Delete flag")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Flag deleted"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Flag not found"),
            @ApiResponse(responseCode = "503", description = "Database unavailable")
    })
    public ResponseEntity<Void> deleteFlag(
            @Parameter(description = "Flag name") @PathVariable String name) {
        flagService.deleteFlag(name);
        return ResponseEntity.noContent().build();
    }
}
