package com.stopforfuel.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads secrets from AWS Secrets Manager and injects them as Spring properties.
 *
 * Activated by setting AWS_SECRET_NAME env var (e.g., "stopforfuel/prod").
 * The secret should be a JSON object with keys matching env var names:
 * {
 *   "DATABASE_URL": "jdbc:postgresql://...",
 *   "DATABASE_USER": "postgres",
 *   "DATABASE_PASSWORD": "...",
 *   "COGNITO_USER_POOL_ID": "...",
 *   "COGNITO_CLIENT_ID": "...",
 *   "COGNITO_ISSUER_URI": "...",
 *   "COGNITO_JWK_URI": "..."
 * }
 */
public class SecretsManagerInitializer implements EnvironmentPostProcessor {

    private static final Map<String, String> SECRET_TO_PROPERTY = Map.of(
            "DATABASE_URL", "spring.datasource.url",
            "DATABASE_USER", "spring.datasource.username",
            "DATABASE_PASSWORD", "spring.datasource.password",
            "COGNITO_ISSUER_URI", "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "COGNITO_JWK_URI", "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            "COGNITO_USER_POOL_ID", "app.cognito.user-pool-id",
            "COGNITO_CLIENT_ID", "app.cognito.client-id"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String secretName = environment.getProperty("AWS_SECRET_NAME");
        if (secretName == null || secretName.isEmpty()) {
            return; // Not using Secrets Manager — skip
        }

        String region = environment.getProperty("AWS_REGION", "ap-south-1");

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .build()) {

            GetSecretValueResponse response = client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretName).build());

            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> secrets = mapper.readValue(
                    response.secretString(), new TypeReference<>() {});

            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, String> entry : secrets.entrySet()) {
                String propertyName = SECRET_TO_PROPERTY.get(entry.getKey());
                if (propertyName != null) {
                    properties.put(propertyName, entry.getValue());
                }
            }

            if (!properties.isEmpty()) {
                environment.getPropertySources().addFirst(
                        new MapPropertySource("aws-secrets-manager", properties));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load secrets from AWS Secrets Manager: " + secretName, e);
        }
    }
}
