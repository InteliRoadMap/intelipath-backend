package com.inteliroadmap.backend.engines;

import com.inteliroadmap.backend.exceptions.BlockedIpException;
import com.inteliroadmap.backend.exceptions.ParsingException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;

import java.io.IOException;

import static org.jsoup.Jsoup.connect;

@Slf4j
public class JsoupEngine {

    public static Document getConnection(String url) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        .header("Accept-Language", "en-US,en;q=0.9,vi;q=0.8")
                        .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"125\", \"Chromium\";v=\"125\", \"Not.A/Brand\";v=\"24\"")
                        .header("Sec-Ch-Ua-Mobile", "?0")
                        .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "none")
                        .header("Sec-Fetch-User", "?1")
                        .header("Upgrade-Insecure-Requests", "1")
                        .timeout(10000)
                        .get();

            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 403) {
                    log.info("Error: Access denied for URL: {}. Please update your user agent", url);
                    throw new BlockedIpException("Error: Access denied for URL: " + url);
                } else if (e.getStatusCode() == 429) {
                    log.info("Error: Too many requests for URL: {}. Please increase your thread sleep duration.", url);
                    throw new BlockedIpException("Error: Too many requests error for URL: " + url);
                }
                if (i == maxRetries - 1) {
                    log.error("Failed to connect to URL after {} retries: {}", maxRetries, url, e);
                    throw new ParsingException("Failed to connect to URL: " + url, e);
                }
            } catch (IOException e) {
                if (i == maxRetries - 1) {
                    log.error("Failed to connect to URL after {} retries: {}", maxRetries, url, e);
                    throw new ParsingException("Failed to connect to URL: " + url, e);
                }
                log.warn("Network error '{}' on attempt {} for {}. Retrying in 2s...", e.getMessage(), i + 1, url);
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new ParsingException("Interrupted during retry sleep", ie);
            }
        }
        return null;
    }
}
