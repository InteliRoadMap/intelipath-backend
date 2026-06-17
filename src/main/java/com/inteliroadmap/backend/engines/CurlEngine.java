package com.inteliroadmap.backend.engines;

import com.inteliroadmap.backend.exceptions.BlockedIpException;
import com.inteliroadmap.backend.exceptions.ParsingException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CurlEngine {

    public static Document getDocument(String url) {
        if (url == null || url.trim().isEmpty()) return null;

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                // Add a small randomized delay to simulate human timing
                Thread.sleep(1000 + (long) (Math.random() * 2000));
                
                List<String> command = new ArrayList<>();
                command.add("curl");
                command.add("-s"); // silent
                command.add("-H");
                command.add("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36");
                command.add("-H");
                command.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
                command.add("-H");
                command.add("Accept-Language: en-US,en;q=0.5");
                command.add("--compressed");
                command.add(url);

                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new ParsingException("Curl process exited with code " + exitCode);
                }

                String pageSource = output.toString();

                if (pageSource.contains("Access denied") && pageSource.contains("Cloudflare") || pageSource.contains("Just a moment...")) {
                    log.info("Error: Blocked by Cloudflare for URL: {}", url);
                    throw new BlockedIpException("Error: Blocked by Cloudflare for URL: " + url);
                }

                return Jsoup.parse(pageSource);

            } catch (BlockedIpException e) {
                throw e; // Bubble up Cloudflare blocks
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("Failed to connect to URL using Curl after {} retries: {}", maxRetries, url, e);
                    throw new ParsingException("Failed to connect to URL: " + url, e);
                }
                log.warn("Error '{}' on attempt {} for {}. Retrying in 2s...", e.getMessage(), i + 1, url);
                
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return null;
    }
}
