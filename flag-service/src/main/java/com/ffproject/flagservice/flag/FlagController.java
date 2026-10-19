package com.ffproject.flagservice.flag;

import com.ffproject.flagservice.flag.dto.CreateFlagRequest;
import com.ffproject.flagservice.flag.dto.FlagResponse;
import com.ffproject.flagservice.flag.dto.UpdateFlagRequest;
import com.ffproject.flagservice.tenant.TenantAccessInterceptor;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD endpoints for feature flags.
 *
 * <p>All routes require a valid JWT <em>and</em> the mandatory
 * {@code X-Tenant-Id} header. {@link TenantAccessInterceptor} validates the
 * header and the caller's access to that tenant before any method here runs, so
 * the header parameters below are guaranteed to be present and authorized.</p>
 *
 * <p>There is deliberately no exception handling in this class — every failure
 * is translated into an HTTP status by
 * {@code GlobalExceptionHandler}.</p>
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
     * Creates a flag in the header-selected tenant.
     *
     * @param tenantId the validated tenant from {@code X-Tenant-Id}
     * @param request  the validated create payload
     * @return {@code 201} with the created flag
     */
    @PostMapping
    @Operation(summary = "Create flag")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Flag created"),
            @ApiResponse(responseCode = "400", description = "Invalid flag name or missing tenant header"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Tenant access denied"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "409", description = "Flag already exists in this tenant"),
            @ApiResponse(responseCode = "503", description = "Database unavailable")
    })
    public ResponseEntity<FlagResponse> createFlag(
            @RequestHeader(TenantAccessInterceptor.TENANT_HEADER) String tenantId,
            @Valid @RequestBody CreateFlagRequest request) {
        Flag created = flagService.createFlag(tenantId.trim(), request.getName(), request.getValue());
        return ResponseEntity.status(HttpStatus.CREATED).body(FlagResponse.from(created));
    }

    /**
     * Lists every live flag in the header-selected tenant.
     *
     * @param tenantId the validated tenant from {@code X-Tenant-Id}
     * @return {@code 200} with the tenant's flags, or an empty array
     */
    @GetMapping
    @Operation(summary = "List all flags in the tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of flags returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Tenant access denied")
    })
    public ResponseEntity<List<FlagResponse>> getAllFlags(
            @RequestHeader(TenantAccessInterceptor.TENANT_HEADER) String tenantId) {
        List<FlagResponse> flags = flagService.getAllFlags(tenantId.trim()).stream()
                .map(FlagResponse::from)
                .toList();
        return ResponseEntity.ok(flags);
    }

    /**
     * Fetches one flag by name within the header-selected tenant.
     *
     * @param tenantId the validated tenant from {@code X-Tenant-Id}
     * @param name     the flag name
     * @return {@code 200} with the flag, or {@code 404} if absent
     */
    @GetMapping("/{name}")
    @Operation(summary = "Get flag by name")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Tenant access denied"),
            @ApiResponse(responseCode = "404", description = "Flag not found")
    })
    public ResponseEntity<FlagResponse> getFlagByName(
            @RequestHeader(TenantAccessInterceptor.TENANT_HEADER) String tenantId,
            @Parameter(description = "Flag name") @PathVariable String name) {
        return ResponseEntity.ok(FlagResponse.from(flagService.getFlagByName(tenantId.trim(), name)));
    }

    /**
     * Updates a flag's boolean value by id within the header-selected tenant.
     *
     * @param tenantId the validated tenant from {@code X-Tenant-Id}
     * @param id       the flag id
     * @param request  the validated update payload
     * @return {@code 200} with the updated flag, or {@code 404} if absent
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Update flag value by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag updated"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Tenant access denied"),
            @ApiResponse(responseCode = "404", description = "Flag not found"),
            @ApiResponse(responseCode = "503", description = "Database unavailable")
    })
    public ResponseEntity<FlagResponse> updateFlag(
            @RequestHeader(TenantAccessInterceptor.TENANT_HEADER) String tenantId,
            @Parameter(description = "Flag id") @PathVariable String id,
            @Valid @RequestBody UpdateFlagRequest request) {
        Flag updated = flagService.updateFlag(tenantId.trim(), id, request.getValue());
        return ResponseEntity.ok(FlagResponse.from(updated));
    }

    /**
     * Deletes a flag by id within the header-selected tenant.
     *
     * @param tenantId the validated tenant from {@code X-Tenant-Id}
     * @param id       the flag id
     * @return {@code 204} with no body, or {@code 404} if absent
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete flag by id")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Flag deleted"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Tenant access denied"),
            @ApiResponse(responseCode = "404", description = "Flag not found"),
            @ApiResponse(responseCode = "503", description = "Database unavailable")
    })
    public ResponseEntity<Void> deleteFlag(
            @RequestHeader(TenantAccessInterceptor.TENANT_HEADER) String tenantId,
            @Parameter(description = "Flag id") @PathVariable String id) {
        flagService.deleteFlag(tenantId.trim(), id);
        return ResponseEntity.noContent().build();
    }
}
