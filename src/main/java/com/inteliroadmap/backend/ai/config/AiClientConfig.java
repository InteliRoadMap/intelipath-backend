package com.inteliroadmap.backend.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the single {@link RestClient} used for all calls to the Python AI
 * service. It is pre-configured with the base URL, the {@code x-api-key} auth
 * header and sensible timeouts, so individual callers never construct their own
 * {@code RestTemplate} nor repeat the auth wiring.
 */
@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiClientConfig {

    /** Main client for work calls (skill extraction, markdown). */
    @Bean
    public RestClient aiServiceRestClient(AiServiceProperties properties) {
        return buildClient(properties, properties.getReadTimeout());
    }

    /** Long-timeout client for job-board scrapes, which run for several minutes. */
    @Bean
    public RestClient aiServiceScraperClient(AiServiceProperties properties) {
        return buildClient(properties, properties.getScrapeTimeout());
    }

    /** Short-timeout client used only by the liveness probe. */
    @Bean
    public RestClient aiServiceHealthClient(AiServiceProperties properties) {
        return buildClient(properties, properties.getHealthTimeout());
    }

    private RestClient buildClient(AiServiceProperties properties, java.time.Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(readTimeout);
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("x-api-key", properties.getApiKey())
                .build();
    }
}
