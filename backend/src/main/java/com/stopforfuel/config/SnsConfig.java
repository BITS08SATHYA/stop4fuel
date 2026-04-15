package com.stopforfuel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * SNS client is only created when stopforfuel.push.enabled=true.
 * In dev, leave it false — the PushNotificationService accepts Optional<SnsClient> and silently skips.
 */
@Configuration
public class SnsConfig {

    @Bean
    @ConditionalOnProperty(name = "stopforfuel.push.enabled", havingValue = "true")
    public SnsClient snsClient() {
        return SnsClient.create();
    }
}
