package com.inteliroadmap.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Description;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Legacy REST bridge retained for a gradual migration to the Python MCP document tool.
 *
 * <p>The AI Mentor no longer registers this function. It calls the MCP
 * {@code extract_document} tool instead. Keep this bridge only for callers that still
 * depend on the REST endpoint during migration.</p>
 */
@Deprecated(forRemoval = false)
@Slf4j
@Service("markItDownTool")
@Description("Legacy REST document extractor. The AI Mentor uses the MCP extract_document tool instead.")
public class MarkItDownTool implements Function<MarkItDownTool.Request, String> {

    @Value("${ai.service.url:http://localhost:8000/api/extract}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public record Request(String fileUrl) {
    }

    @Override
    public String apply(Request request) {
        log.info("Legacy MarkItDown REST bridge called for URL: {}", request.fileUrl());

        if (request.fileUrl() == null || request.fileUrl().isBlank()) {
            return "[TOOL_ERROR] No URL provided. Please ask the user to share the document URL.";
        }

        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("url", request.fileUrl());

            ResponseEntity<Map> response = restTemplate.postForEntity(aiServiceUrl, requestBody, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String markdown = (String) response.getBody().get("markdown");
                if (markdown == null || markdown.isBlank()) {
                    return "[TOOL_ERROR] The document was fetched but no text could be extracted.";
                }
                return markdown;
            }
            return "[TOOL_ERROR] Document extraction service returned status: " + response.getStatusCode();
        } catch (ResourceAccessException exception) {
            log.error("Legacy MarkItDown REST bridge is unreachable at: {}", aiServiceUrl, exception);
            return "[TOOL_ERROR] The document extraction service is currently unavailable.";
        } catch (Exception exception) {
            log.error("Legacy MarkItDown REST bridge failed for URL: {}", request.fileUrl(), exception);
            return "[TOOL_ERROR] Unexpected error: " + exception.getMessage();
        }
    }
}
