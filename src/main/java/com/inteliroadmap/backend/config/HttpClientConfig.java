package com.inteliroadmap.backend.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Shared {@link RestTemplate} for outbound calls to third-party HTTP APIs
 * (GitHub, Supabase storage). Centralising it guarantees every caller inherits
 * sane connect/read timeouts instead of the infinite defaults of a bare
 * {@code new RestTemplate()}, which can hang a request thread indefinitely when
 * the remote host is slow.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate externalApiRestTemplate() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(30));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return new RestTemplate(requestFactory);
    }
}
