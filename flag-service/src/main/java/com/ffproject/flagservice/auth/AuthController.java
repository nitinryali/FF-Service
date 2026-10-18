package com.ffproject.flagservice.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Login endpoint for the Flag Service.
 *
 * <p>Exposes {@code POST /auth/login}, which exchanges admin credentials for a
 * signed JWT. Every failure mode — bad credentials, blank fields, or fields
 * that violate the length constraints — returns an identical
 * {@code 401 {"error":"Authentication failed"}} body so that the caller cannot
 * tell which field was wrong (Requirements 2.1, 2.6).</p>
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final String AUTH_FAILURE_MESSAGE = "Authentication failed";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates the submitted credentials and returns a signed JWT.
     *
     * @param request the validated login payload
     * @return {@code 200 {"token": "..."}} on success, or
     *         {@code 401 {"error": "Authentication failed"}} on failure
     */
    @PostMapping("/login")
    @Operation(summary = "Issue a JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JWT token returned"),
            @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        try {
            authService.authenticate(request.getUsername(), request.getPassword());
        } catch (BadCredentialsException ex) {
            return unauthorized();
        }

        String token = authService.generateToken(request.getUsername());
        return ResponseEntity.ok(Map.of("token", token));
    }

    /**
     * Maps bean-validation failures on the login payload to the same generic
     * 401 response used for bad credentials.
     *
     * <p>This handler lives on the controller (and therefore takes precedence over
     * {@code GlobalExceptionHandler}'s {@code MethodArgumentNotValidException} →
     * 400 mapping) because Requirement 2.6 mandates 401 for missing, empty, or
     * invalid credentials at the login endpoint, while Requirement 1.9 mandates
     * 400 for invalid flag names elsewhere.</p>
     *
     * @param ex the validation failure raised before the controller method ran
     * @return {@code 401 {"error": "Authentication failed"}}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationFailure(MethodArgumentNotValidException ex) {
        return unauthorized();
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", AUTH_FAILURE_MESSAGE));
    }

    /**
     * Login request payload.
     *
     * <p>Field bounds mirror Requirement 2.1: username 1–64 characters, password
     * 1–128 characters.</p>
     */
    @Getter
    @Setter
    public static class LoginRequest {

        @NotBlank
        @Size(max = 64)
        private String username;

        @NotBlank
        @Size(max = 128)
        private String password;
    }
}
