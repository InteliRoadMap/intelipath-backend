package com.inteliroadmap.backend.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.inteliroadmap.backend.domain.dto.response.scraper.ScraperResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Single entry point for every call to the Python AI service. Wraps the shared,
 * pre-authenticated {@link RestClient} so callers express intent
 * ({@code extractSkills}, {@code extractMarkdown}, ...) instead of repeating URLs,
 * the {@code x-api-key} header and {@code RestTemplate} boilerplate.
 */
@Slf4j
@Component
public class AiServiceClient {

    private final RestClient restClient;
    private final RestClient scraperClient;
    private final RestClient healthClient;

    public AiServiceClient(RestClient aiServiceRestClient, RestClient aiServiceScraperClient, RestClient aiServiceHealthClient) {
        this.restClient = aiServiceRestClient;
        this.scraperClient = aiServiceScraperClient;
        this.healthClient = aiServiceHealthClient;
    }

    public record SkillExtractionRequest(List<String> descriptions) {}

    public record SkillExtractionResponse(@JsonProperty("skills_per_doc") List<List<String>> skillsPerDoc) {}

    /**
     * Extract skills from a batch of descriptions. Returns one skill list per
     * input document, in the same order.
     */
    public List<List<String>> extractSkills(List<String> descriptions) {
        // Batch skill extraction runs one LLM pass per description, so a full
        // recruitment set easily exceeds the normal read timeout — use the
        // long-timeout scraper client (same class of long-running AI work).
        SkillExtractionResponse response = scraperClient.post()
                .uri("/api/extract-skills")
                .body(new SkillExtractionRequest(descriptions))
                .retrieve()
                .body(SkillExtractionResponse.class);
        return response != null && response.skillsPerDoc() != null ? response.skillsPerDoc() : List.of();
    }

    /**
     * Extract markdown text from a public document URL (PDF, DOCX, image, ...).
     *
     * @return the extracted markdown, or {@code null} if nothing usable was returned.
     */
    public String extractMarkdown(String fileUrl) {
        Map<?, ?> response = restClient.post()
                .uri("/api/extract")
                .body(Map.of("url", fileUrl))
                .retrieve()
                .body(Map.class);
        Object markdown = response != null ? response.get("markdown") : null;
        return markdown != null ? markdown.toString() : null;
    }

    /**
     * Trigger a TopCV scrape and return the processed companies, recruitments
     * and posts.
     */
    public ScraperResponse triggerTopCvScrape(int limit) {
        return scraperClient.get()
                .uri("/api/v1/scraper/scrape/topcv/{limit}", limit)
                .retrieve()
                .body(ScraperResponse.class);
    }

    /**
     * Trigger an ITviec scrape and return the processed companies, recruitments
     * and posts. Same response shape as {@link #triggerTopCvScrape(int)} — the raw
     * tables are source-agnostic, only the id prefix ({@code itviec.*}) differs.
     */
    public ScraperResponse triggerItviecScrape(int limit) {
        return scraperClient.get()
                .uri("/api/v1/scraper/scrape/itviec/{limit}", limit)
                .retrieve()
                .body(ScraperResponse.class);
    }

    public record ScrapeStartResponse(String jobId) {}

    /**
     * Queue a scrape on the AI service and return the job id to poll.
     *
     * <p>The synchronous {@code /scrape/...} routes hold the connection open for the whole
     * run, so anything but a small limit outlives the read timeout and the finished result
     * is thrown away. Starting a job returns immediately instead.
     *
     * @return the job id to poll, or {@code null} if the service returned nothing.
     */
    public String startScrape(String source, int limit) {
        ScrapeStartResponse response = scraperClient.post()
                .uri("/api/v1/scraper/jobs/{source}/{limit}", source, limit)
                .retrieve()
                .body(ScrapeStartResponse.class);
        return response != null ? response.jobId() : null;
    }

    /** Poll a scrape job. Returns the raw status node (state/phase/message/error/result). */
    public JsonNode getScrapeStatus(String jobId) {
        return scraperClient.get()
                .uri("/api/v1/scraper/jobs/{jobId}", jobId)
                .retrieve()
                .body(JsonNode.class);
    }

    public record FlmSyncStartResponse(String jobId) {}

    /**
     * Kick off an FLM sync on the AI service. {@code body} carries the admin-supplied
     * cookie, the curriculum id / prefixes and the catalog skill names; it is a plain
     * map so the (sensitive) cookie never becomes a typed, loggable field here.
     *
     * @return the job id to poll, or {@code null} if the service returned nothing.
     */
    public String startFlmSync(Object body) {
        FlmSyncStartResponse response = restClient.post()
                .uri("/api/v1/flm/sync")
                .body(body)
                .retrieve()
                .body(FlmSyncStartResponse.class);
        return response != null ? response.jobId() : null;
    }

    /** Poll an FLM sync job. Returns the raw status node (state/phase/progress/overlay). */
    public JsonNode getFlmSyncStatus(String jobId) {
        return restClient.get()
                .uri("/api/v1/flm/sync/{jobId}", jobId)
                .retrieve()
                .body(JsonNode.class);
    }

    /** Lightweight liveness probe against the service root (short timeout). */
    public boolean isHealthy() {
        try {
            healthClient.get().uri("/").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("AiServiceClient: health check failed: {}", e.getMessage());
            return false;
        }
    }
}
