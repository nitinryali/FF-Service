package com.ffproject.flagservice.auth;

import com.ffproject.flagservice.auth.dto.CreateUserRequest;
import com.ffproject.flagservice.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account management. Platform administrators only.
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Creates a tenant-bound user.
     *
     * @param request the validated create payload
     * @return {@code 201} with the created account (never the password hash)
     */
    @PostMapping
    @Operation(summary = "Create a tenant-bound user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Invalid payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Platform admin role required"),
            @ApiResponse(responseCode = "404", description = "Tenant not found"),
            @ApiResponse(responseCode = "409", description = "Username already exists")
    })
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        AppUser created = userService.createUser(
                request.getUsername(),
                request.getPassword(),
                request.getTenantId(),
                request.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }
}
