package com.backend.nirvana.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration class for WebClient beans.
 */
@Configuration
public class WebClientConfig {
    
    /**
     * Provides a WebClient.Builder bean for dependency injection.
     * 
     * @return WebClient.Builder instance
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}