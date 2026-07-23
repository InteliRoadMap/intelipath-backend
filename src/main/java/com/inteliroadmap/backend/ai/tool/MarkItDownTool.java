package com.inteliroadmap.backend.ai.tool;

import com.inteliroadmap.backend.ai.client.AiServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Function;

/**
 * Spring AI tool that lets the virtual mentor read text out of any public
 * document URL (PDF, DOCX, XLSX, PPTX, images) via the AI service.
 */
@Slf4j
@RequiredArgsConstructor
@Service("markItDownTool")
@Description("Use this tool to read and extract text from ANY document URL (PDF, DOCX, XLSX, PPTX, Images). Provide the full public URL of the document.")
public class MarkItDownTool implements Function<MarkItDownTool.Request, String> {

    private final AiServiceClient aiServiceClient;

    public record Request(String fileUrl) {}

    @Override
    public String apply(Request request) {
        log.info("MarkItDownTool: AI called MarkItDownTool for URL: {}", request.fileUrl());

        if (request.fileUrl() == null || request.fileUrl().isBlank()) {
            return "[TOOL_ERROR] No URL provided. Please ask the user to share the document URL.";
        }

        try {
            String markdown = aiServiceClient.extractMarkdown(request.fileUrl());
            if (markdown == null || markdown.isBlank()) {
                return "[TOOL_ERROR] The document was fetched but no text could be extracted. "
                        + "The file may be empty, corrupted, or an unsupported format.";
            }
            log.info("MarkItDownTool: extracted {} chars from: {}", markdown.length(), request.fileUrl());
            return markdown;
        } catch (ResourceAccessException e) {
            log.error("MarkItDownTool: document extraction service is unreachable", e);
            return "[TOOL_ERROR] The document extraction service is currently unavailable. "
                    + "Tell the user the document reader is temporarily under maintenance and to try again later.";
        } catch (Exception e) {
            log.error("MarkItDownTool: unexpected error for URL: {}", request.fileUrl(), e);
            return "[TOOL_ERROR] Unexpected error: " + e.getMessage()
                    + ". Ask the user to verify the document URL is publicly accessible.";
        }
    }
}
