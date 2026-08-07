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

    private String baseUrl = "http://localhost:8000";

    /** Shared secret sent as the {@code x-api-key} header on every call. */
    private String apiKey = "";

    /** How long to wait to establish a connection. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * How long to wait for a response. Kept generous because scraping and
     * batch AI summarisation are long-running operations.
     *
     * <p>Raised from 300s when skill extraction stopped skipping postings. It used to
     * send only the descriptions a keyword pass came up thin on — a minority — and 300s
     * covered that comfortably. Reading all 912 in a run at concurrency 12 lands around
     * 150s, which 300s would also cover, but only until the corpus grows: a timeout here
     * does not merely fail, it discards a completed run of paid model calls. The headroom
     * is cheap and the failure is not.
     */
    private Duration readTimeout = Duration.ofSeconds(900);

    /**
     * Read timeout for job-board scrapes. A scrape walks many detail pages with
     * polite per-request delays and then AI-summarises every row, so it routinely
     * runs several minutes — far longer than a normal work call.
     */
    private Duration scrapeTimeout = Duration.ofSeconds(900);

    /**
     * How long to keep polling a background scrape before giving up on it.
     *
     * <p>This, not {@link #scrapeTimeout}, is what bounds a scrape now that the work runs
     * as a job on the AI service: the HTTP calls that start and poll it return instantly.
     * A posting costs a detail fetch plus an LLM enrichment call — around six seconds all
     * in — so this budget is what decides the largest workable SCRAPER_LIMIT.
     *
     * <p>Two hours carries roughly a thousand postings at that rate. Raised from forty
     * minutes because a two-hundred-posting sample is too small to weigh anything: with
     * that many, one company posting eighteen roles is nine percent of the entire
     * "market" and every trend is really a handful of rows.
     */
    private Duration scrapeJobTimeout = Duration.ofMinutes(120);

    /** Gap between polls of a running scrape. */
    private Duration scrapePollInterval = Duration.ofSeconds(10);

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

    public Duration getScrapeTimeout() {
        return scrapeTimeout;
    }

    public void setScrapeTimeout(Duration scrapeTimeout) {
        this.scrapeTimeout = scrapeTimeout;
    }

    public Duration getHealthTimeout() {
        return healthTimeout;
    }

    public void setHealthTimeout(Duration healthTimeout) {
        this.healthTimeout = healthTimeout;
    }

    public Duration getScrapeJobTimeout() {
        return scrapeJobTimeout;
    }

    public void setScrapeJobTimeout(Duration scrapeJobTimeout) {
        this.scrapeJobTimeout = scrapeJobTimeout;
    }

    public Duration getScrapePollInterval() {
        return scrapePollInterval;
    }

    public void setScrapePollInterval(Duration scrapePollInterval) {
        this.scrapePollInterval = scrapePollInterval;
    }
}
