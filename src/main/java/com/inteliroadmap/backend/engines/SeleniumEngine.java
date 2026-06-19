package com.inteliroadmap.backend.engines;

import com.inteliroadmap.backend.exceptions.BlockedIpException;
import com.inteliroadmap.backend.exceptions.ParsingException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

@Slf4j
public class SeleniumEngine {

    public static Document getDocument(WebDriver driver, String url) {
        if (url == null || url.trim().isEmpty()) return null;

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                // Navigate to the URL
                driver.get(url);

                // Add a small delay to allow JS rendering and simulate human delay
                try {
                    Thread.sleep(3000 + (long) (Math.random() * 1000));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ParsingException("Interrupted during wait", ie);
                }

                String title = driver.getTitle();
                
                // If Cloudflare challenge appears, give the user 60 seconds to solve it manually
                if (title != null && (title.contains("Cloudflare") || title.contains("Just a moment"))) {
                    log.info("Cloudflare challenge detected! Please solve it manually in the browser. Waiting up to 60 seconds...");
                    try {
                        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
                        wait.until(d -> {
                            String t = d.getTitle();
                            return t != null && !t.contains("Cloudflare") && !t.contains("Just a moment");
                        });
                        log.info("Cloudflare challenge solved. Resuming scrape.");
                        // Wait an extra 2 seconds for the actual page to fully load after solving
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        log.info("Error: Blocked by Cloudflare challenge for URL (timeout 60s): {}", url);
                        throw new BlockedIpException("Error: Blocked by Cloudflare for URL: " + url);
                    }
                }

                String pageSource = driver.getPageSource();
                if (pageSource.contains("Access denied") && pageSource.contains("Cloudflare")) {
                    log.info("Error: Access denied by Cloudflare for URL: {}", url);
                    throw new BlockedIpException("Error: Access denied for URL: " + url);
                }

                return Jsoup.parse(pageSource);

            } catch (BlockedIpException e) {
                throw e;
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("Failed to connect to URL using Selenium after {} retries: {}", maxRetries, url, e);
                    throw new ParsingException("Failed to connect to URL: " + url, e);
                }
                log.warn("Error '{}' on attempt {} for {}. Retrying in 2s...", e.getMessage(), i + 1, url);
                
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ParsingException("Interrupted during retry sleep", ie);
                }
            }
        }
        return null;
    }
}
