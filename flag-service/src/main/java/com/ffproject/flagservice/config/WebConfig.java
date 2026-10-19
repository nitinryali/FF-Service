package com.ffproject.flagservice.config;

import com.ffproject.flagservice.tenant.TenantAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration.
 *
 * <p>Registers {@link TenantAccessInterceptor} on the flag routes so that every
 * flag request is tenant-scoped before a controller method runs. Tenant and
 * user management routes are intentionally not covered — they are authorized by
 * role, not by tenant header.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantAccessInterceptor tenantAccessInterceptor;

    public WebConfig(TenantAccessInterceptor tenantAccessInterceptor) {
        this.tenantAccessInterceptor = tenantAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantAccessInterceptor)
                .addPathPatterns("/flags", "/flags/**");
    }
}
