package com.stopforfuel.config;

import com.stopforfuel.backend.config.AiRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    // CORS is now managed centrally by SecurityConfig.corsConfigurationSource()

    private final ObjectProvider<AiRateLimitInterceptor> aiRateLimitProvider;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        AiRateLimitInterceptor aiRateLimit = aiRateLimitProvider.getIfAvailable();
        if (aiRateLimit != null) {
            registry.addInterceptor(aiRateLimit)
                    .addPathPatterns("/api/ai-analytics/**");
        }
    }
}
