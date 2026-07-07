package com.inteliroadmap.backend.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Strongly-typed configuration for the Python AI/scraper service
 * (intelipath-service). Centralises the base URL, shared API key and HTTP
 * timeouts that were previously duplicated across several classes under three
 * different property names.
 */
@ConfigurationProperties(prefix = "ai-service")
public class AiServiceProperties {

    /** Root URL of the AI service, e.g. {@code http://ai-service:8000}. */
    private String baseUrl = "http://localhost:8000";

    /** Shared secret sent as the {@code x-api-key} header on every call. */
    private String apiKey = "";

    /** How long to wait to establish a connection. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * How long to wait for a response. Kept generous because scraping and
     * batch AI summarisation are long-running operations.
     */
    private Duration readTimeout = Duration.ofSeconds(300);

    /**
     * Short timeout used only by the liveness probe, so a slow or down service
     * never hangs the admin dashboard.
     */
    private Duration healthTimeout = Duration.ofSeconds(2);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getHealthTimeout() {
        return healthTimeout;
    }

    public void setHealthTimeout(Duration healthTimeout) {
        this.healthTimeout = healthTimeout;
    }
}
