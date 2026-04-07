package com.stopforfuel.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConfigurationProperties(prefix = "app.paytm")
@Getter
@Setter
public class PaytmConfig {

    private boolean enabled = false;
    private String mid;
    private String storeId;
    private String clientId;
    private String clientSecret;
    private String baseUrl = "https://securegw-stage.paytm.in";
    private String callbackUrl = "http://localhost:8080/api/paytm/callback";
    private int timeoutMinutes = 5;

    @Bean
    public WebClient paytmWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
