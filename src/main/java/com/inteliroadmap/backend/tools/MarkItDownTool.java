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

@Slf4j
@Deprecated(forRemoval = false)
@Service("markItDownTool")
@Description("Use this tool to read and extract text from ANY document URL (PDF, DOCX, XLSX, PPTX, Images). Provide the full public URL of the document.")
public class MarkItDownTool implements Function<MarkItDownTool.Request, String> {

    @Value("${ai.service.url:http://localhost:8000/api/extract}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public record Request(String fileUrl) {}

    @Override
    public String apply(Request request) {
        log.info("AI Called MarkItDownTool for URL: {}", request.fileUrl());

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
                    return "[TOOL_ERROR] The document was fetched but no text could be extracted. The file may be empty, corrupted, or an unsupported format.";
                }
                log.info("MarkItDownTool extracted {} chars from: {}", markdown.length(), request.fileUrl());
                return markdown;
            } else {
                return "[TOOL_ERROR] Document extraction service returned status: " + response.getStatusCode();
            }
        } catch (ResourceAccessException e) {
            log.error("MarkItDown Python service is unreachable at: {}", aiServiceUrl, e);
            return "[TOOL_ERROR] The document extraction service is currently unavailable. Tell the user: 'Dịch vụ đọc tài liệu hiện đang bảo trì, vui lòng thử lại sau.'";
        } catch (Exception e) {
            log.error("MarkItDownTool unexpected error for URL: {}", request.fileUrl(), e);
            return "[TOOL_ERROR] Unexpected error: " + e.getMessage() + ". Ask the user to verify the document URL is publicly accessible.";
        }
    }
}
