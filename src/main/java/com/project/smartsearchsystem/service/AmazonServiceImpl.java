package com.project.smartsearchsystem.service;

import com.project.smartsearchsystem.dto.AmazonBookDto;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AmazonServiceImpl implements AmazonService {
    private WebDriver driver;
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final String AMAZON_DP_URL = "https://www.amazon.com/dp/";

    @PostConstruct
    public void initialize() {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions()
                    .addArguments("--headless")
                    .addArguments("user‑agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .addArguments("--disable-blink-features=AutomationControlled");
            driver = new ChromeDriver(options);
        } catch (Exception e) {
            log.warn("Could not initialize ChromeDriver – Amazon searches will be disabled", e);
            driver = null;
        }
    }

    @Override
    public List<AmazonBookDto> searchAmazonBooks(String userInput) {
        if (driver == null) {
            return List.of();
        }

        List<AmazonBookDto> results = new ArrayList<>();
        try {
            String url = "https://www.amazon.com/s?k=" + URLEncoder.encode(userInput, StandardCharsets.UTF_8) + "&i=stripbooks";
            System.out.println("Request URL: " + url);

            driver.get(url);

            if (!waitForMainSlot(driver)) {
                System.out.println("Failed to load main content after retries.");
                return results;
            }

            Thread.sleep(3000);

            System.out.println("Page title: " + driver.getTitle());

            List<WebElement> bookItems = driver.findElements(
                    By.cssSelector("div.s-main-slot div[data-component-type='s-search-result']")
            );
            System.out.println("Found " + bookItems.size() + " book items");

            for (WebElement book : bookItems) {
                try {
                    // Get the title of the book
                    String title = "N/A";
                    try {
                        List<WebElement> h2Elements = book.findElements(By.tagName("h2"));
                        for (WebElement h2 : h2Elements) {
                            String ariaLabelValue = h2.getDomAttribute("aria-label");
                            if (ariaLabelValue != null && !ariaLabelValue.isEmpty()) {
                                title = ariaLabelValue;
                                break;
                            }
                            else {
                                try {
                                    String spanText = h2.findElement(By.tagName("span")).getText();
                                    if (!spanText.isEmpty()) {
                                        title = spanText;
                                        break;
                                    }
                                } catch (Exception ex) {
                                    // Continue checking next h2 element
                                    if (!h2.getText().isEmpty()) {
                                        title = h2.getText();
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Failed to extract title: " + e.getMessage());
                    }

                    // Get the author(s)
                    String author = "N/A";
                    try {
                        List<WebElement> metadataRows = book.findElements(By.cssSelector("div.a-row.a-size-base.a-color-secondary"));

                        for (WebElement row : metadataRows) {
                            String text = row.getText().trim();

                            if (text.toLowerCase().startsWith("by ")) {
                                String cleanText = text.substring(3).trim();

                                cleanText = cleanText.replaceAll("(?i)\\s+and\\s+", ", ");

                                if (cleanText.contains("|")) {
                                    cleanText = cleanText.substring(0, cleanText.indexOf("|")).trim();
                                }

                                author = cleanText;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Failed to extract authors: " + e.getMessage());
                    }

                    // Get ISBN
                    String isbn = book.getDomAttribute("data-asin");

                    // Get image of the book
                    String imageUrl = "/placeholder.jpg";
                    try {
                        imageUrl = book.findElements(By.cssSelector("img.s-image")).getFirst().getDomAttribute("src");
                    } catch (Exception e) {
                        System.out.println("Failed to extract image: " + e.getMessage());
                    }
                    results.add(new AmazonBookDto(title, author, isbn, imageUrl));
                } catch (Exception e) {
                    System.out.println("Error processing book item: " + e.getMessage());
                }
            }
            return results;
        } catch (Exception e) {
            System.out.println("General error in searchAmazonBooks: " + e.getMessage());
            return List.of();
        }
    }

    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            driver.quit();
        }
    }

    private boolean waitForMainSlot(WebDriver driver) {
        int attempts = 0;
        while (attempts < 3) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.s-main-slot")));
                return true;
            } catch (Exception e) {
                System.out.println("Attempt " + (attempts + 1) + " failed. Retrying...");
                attempts++;
                driver.navigate().refresh();
                try {
                    Thread.sleep(5000); // Wait additional time after refresh
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return false;
    }
}