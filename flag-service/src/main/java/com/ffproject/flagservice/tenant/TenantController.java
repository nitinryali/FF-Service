package com.ffproject.flagservice.tenant;

import com.ffproject.flagservice.tenant.dto.CreateTenantRequest;
import com.ffproject.flagservice.tenant.dto.TenantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tenant lifecycle. Platform administrators only.
 */
@RestController
@RequestMapping("/tenants")
@Tag(name = "Tenants")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Creates a tenant.
     */
    @PostMapping
    @Operation(summary = "Create a tenant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant created"),
            @ApiResponse(responseCode = "400", description = "Invalid tenant name"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Platform admin role required"),
            @ApiResponse(responseCode = "409", description = "Tenant already exists")
    })
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Tenant created = tenantService.createTenant(request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(created));
    }

    /**
     * Lists every tenant.
     */
    @GetMapping
    @Operation(summary = "List tenants")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenants returned"),
            @ApiResponse(responseCode = "403", description = "Platform admin role required")
    })
    public ResponseEntity<List<TenantResponse>> getAllTenants() {
        List<TenantResponse> tenants = tenantService.getAllTenants().stream()
                .map(TenantResponse::from)
                .toList();
        return ResponseEntity.ok(tenants);
    }

    /**
     * Fetches one tenant by id.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a tenant by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant returned"),
            @ApiResponse(responseCode = "403", description = "Platform admin role required"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<TenantResponse> getTenant(@PathVariable String id) {
        return ResponseEntity.ok(TenantResponse.from(tenantService.getTenant(id)));
    }

    /**
     * Deletes a tenant and every flag and user that belongs to it.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tenant and all of its flags")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tenant deleted"),
            @ApiResponse(responseCode = "403", description = "Platform admin role required"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<Void> deleteTenant(@PathVariable String id) {
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}
