package com.inteliroadmap.backend.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Description;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service("markItDownTool")
@Description("Use this tool to read and extract text from ANY document URL (PDF, DOCX, XLSX, PPTX, Images). Provide the URL of the document.")
public class MarkItDownTool implements Function<MarkItDownTool.Request, String> {

    @Value("${ai.service.url:http://localhost:8000/api/extract}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public record Request(String fileUrl) {}

    @Override
    public String apply(Request request) {
        log.info("AI Called MarkItDownTool to extract text from URL: {}", request.fileUrl());
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("url", request.fileUrl());

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl,
                    requestBody,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("markdown");
            } else {
                return "Error: Could not extract document content. Status: " + response.getStatusCode();
            }
        } catch (Exception e) {
            log.error("Error calling MarkItDown service", e);
            return "Error calling MarkItDown service: " + e.getMessage();
        }
    }
}
