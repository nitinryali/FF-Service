package com.ffproject.flagservice.config;

import com.ffproject.flagservice.auth.JwtFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the Flag Service.
 *
 * <p>The service is fully stateless: CSRF protection is disabled, no session is
 * ever created, and authentication is performed exclusively by
 * {@link JwtFilter}, which is registered ahead of
 * {@link UsernamePasswordAuthenticationFilter} so that it runs before any
 * authorization decision.</p>
 *
 * <p>The login endpoint and the OpenAPI/Swagger UI assets are public; every
 * other request must carry a valid JWT.</p>
 *
 * <p>Method security is enabled so platform-only routes can be guarded with
 * {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')")}. Passwords are stored as
 * BCrypt hashes in the {@code users} collection, hence the
 * {@link PasswordEncoder} bean.</p>
 *
 * <p>CORS is enabled so browser clients on another origin (for example the
 * React UI) can call the API directly. The allowed origins come from
 * {@code app.cors.allowed-origins}. Because authentication uses a Bearer token
 * and never a cookie, {@code allowCredentials} is false and the default of
 * {@code *} exposes no ambient authority.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /**
     * Defines the single security filter chain used by the service.
     *
     * @param http the Spring Security builder
     * @return the configured filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/auth/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS policy for browser clients.
     *
     * <p>Spring Security installs a {@code CorsFilter} ahead of the JWT filter,
     * so an {@code OPTIONS} preflight is answered here and never reaches
     * {@link JwtFilter} (which would otherwise reject it with a 401 for the
     * missing token).</p>
     *
     * <p>{@code allowCredentials} is deliberately false: the API authenticates
     * with a Bearer token, so the browser sends no cookies and a wildcard origin
     * grants no ambient authority.</p>
     *
     * @param allowedOrigins origins from {@code app.cors.allowed-origins}
     * @return the CORS configuration applied to every path
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id"));
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * BCrypt password hashing for accounts stored in the {@code users}
     * collection.
     *
     * @return the password encoder used at login and user creation
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Prevents {@link JwtFilter} from being auto-registered with the servlet
     * container.
     *
     * <p>Because {@link JwtFilter} is a Spring bean, Spring Boot would otherwise
     * register it as a container-level filter <em>in addition</em> to the
     * security chain registration above. Disabling that registration guarantees
     * the filter runs exactly once, inside the security chain, where it belongs.</p>
     *
     * @param jwtFilter the filter bean to de-register
     * @return a disabled registration descriptor
     */
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter jwtFilter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
        registration.setEnabled(false);
        return registration;
    }
}
