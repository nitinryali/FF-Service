package com.ffproject.flagservice.config;

import com.ffproject.flagservice.auth.JwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
 * other request must carry a valid JWT (Requirements 1.6, 2.2, 2.7).</p>
 *
 * <p>No {@code PasswordEncoder} is declared: credentials are compared directly
 * against the configured admin account inside {@code AuthService} and are never
 * hashed or stored, so there is no use for one here.</p>
 */
@Configuration
@EnableWebSecurity
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
