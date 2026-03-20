package com.stopforfuel.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
public class CognitoConfig {

    @Value("${app.cognito.region:ap-south-1}")
    private String region;

    @Bean
    @ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true", matchIfMissing = true)
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.auth.enabled", havingValue = "false")
    public CognitoIdentityProviderClient noOpCognitoClient() {
        // Return a client that won't be used in dev mode
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }
}
