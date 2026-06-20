package com.inteliroadmap.backend.parsers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkedInParser {

    public void parseLinkedInJobs() {
        ChromeOptions options = new ChromeOptions();    // |
        options.addArguments("--headless=new");         // | Stop open browser on screen
        WebDriver driver = new ChromeDriver(options);   // |

        // Wait for element to load
//        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            driver.get("https://example.com");

            String title = driver.getTitle();
            System.out.println("Page title: " + title);

            WebElement heading =
                    driver.findElement(By.tagName("h1"));

            // Wait for element to load
//            WebElement jobTitle = wait.until(
//                    ExpectedConditions.visibilityOfElementLocated(
//                            By.className("job-title")
//                    )
//            );

//            List<WebElement> jobs =
//                    driver.findElements(By.cssSelector(".job-title"));
//
//            for (WebElement job : jobs) {
//                System.out.println(job.getText());
//            }

//            while (true) {
//
//                List<WebElement> jobs =
//                        driver.findElements(By.cssSelector(".job-title"));
//
//                for (WebElement job : jobs) {
//                    System.out.println(job.getText());
//                }
//
//                List<WebElement> nextButtons =
//                        driver.findElements(By.cssSelector(".next"));
//
//                if (nextButtons.isEmpty()) {
//                    break;
//                }
//
//                nextButtons.get(0).click();
//            }

            System.out.println("Heading: " + heading.getText());

            try (BufferedWriter writer =
                         new BufferedWriter(new FileWriter("jobs.txt"))) {
                writer.write(title + "\n" + heading.getText());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } finally {
            driver.quit();
        }
    }
}
