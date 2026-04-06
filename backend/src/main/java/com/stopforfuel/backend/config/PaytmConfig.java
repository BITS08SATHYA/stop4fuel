package com.stopforfuel.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.paytm")
@Getter
@Setter
public class PaytmConfig {
    private String merchantId;
    private String merchantKey;
    private String baseUrl = "https://securegw.paytm.in";
    private String website = "DEFAULT";
    private boolean syncEnabled = false;
}
