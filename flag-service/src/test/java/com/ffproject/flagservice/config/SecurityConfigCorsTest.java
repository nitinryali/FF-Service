package com.ffproject.flagservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS policy tests.
 *
 * <p>These exercise the {@link CorsConfigurationSource} through a real
 * {@link CorsFilter} in a standalone MockMvc setup, so they need neither the
 * application context nor MongoDB. They cover the behaviour a browser depends
 * on: which origins, methods, and headers survive a preflight.</p>
 */
class SecurityConfigCorsTest {

    private MockMvc mockMvcFor(List<String> allowedOrigins) {
        CorsConfigurationSource source =
                new SecurityConfig(null).corsConfigurationSource(allowedOrigins);
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new CorsFilter(source))
                .build();
    }

    @Test
    void preflightFromAnyOriginIsAllowedByDefault() throws Exception {
        // allowedOriginPatterns echoes the caller's origin rather than a literal "*".
        mockMvcFor(List.of("*")).perform(options("/flags")
                        .header(HttpHeaders.ORIGIN, "https://ui.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization,x-tenant-id,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://ui.example.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("PATCH")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("x-tenant-id")));
    }

    @Test
    void preflightIsNotRejectedByAuthentication() throws Exception {
        // A preflight carries no Authorization header; it must not 401.
        mockMvcFor(List.of("*")).perform(options("/flags")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk());
    }

    @Test
    void disallowedMethodIsRejected() throws Exception {
        mockMvcFor(List.of("*")).perform(options("/flags")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void restrictedListAllowsOnlyThatOrigin() throws Exception {
        mockMvcFor(List.of("http://localhost:5173")).perform(options("/flags")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"));
    }

    @Test
    void restrictedListRejectsOtherOrigins() throws Exception {
        mockMvcFor(List.of("http://localhost:5173")).perform(options("/flags")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualRequestCarriesTheAllowOriginHeader() throws Exception {
        mockMvcFor(List.of("*")).perform(get("/flags")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"));
    }

    /** Stands in for a real endpoint; the preflight never reaches it. */
    @RestController
    static class ProbeController {

        @GetMapping("/flags")
        String flags() {
            return "ok";
        }
    }
}
