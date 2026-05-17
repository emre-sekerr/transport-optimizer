package com.transport.optimizer.config;

import lombok.Data;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@Configuration
public class OrsConfig {

    @Bean
    @ConfigurationProperties(prefix = "ors.api")
    public OrsProperties orsProperties() {
        return new OrsProperties();
    }

    @Bean
    public OkHttpClient okHttpClient(OrsProperties orsProperties) {
        return new OkHttpClient.Builder()
                .connectTimeout(orsProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(orsProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(orsProperties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Data
    public static class OrsProperties {
        private String baseUrl = "https://api.openrouteservice.org";
        private String key;
        private int timeoutSeconds = 30;
        private int maxRetries = 3;
    }
}
