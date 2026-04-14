package com.stopforfuel.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;

@Configuration
@ConditionalOnProperty(value = "management.cloudwatch.metrics.export.enabled", havingValue = "true")
public class CloudWatchClientConfig {

    // Required bean for Spring Boot's CloudWatch2MetricsExportAutoConfiguration:
    // it creates the CloudWatchMeterRegistry only when a CloudWatchAsyncClient is present.
    // Region is picked up from the AWS_REGION env var set by ECS.
    @Bean
    public CloudWatchAsyncClient cloudWatchAsyncClient() {
        return CloudWatchAsyncClient.builder().build();
    }
}
