package fr.abes.bacon.baconediteurs.batch.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudflareBypass {

    public static Document fetchDocumentWithSelenium(String url, String waitedElementSelector) throws IOException {
        Path tempProfile = Files.createTempDirectory("chrome-profile-" + UUID.randomUUID());
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
            "--headless",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--window-size=1920,1080",
            "--remote-allow-origins=*",
            "--disable-blink-features=AutomationControlled",
            "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36",
            "--user-data-dir=" + tempProfile.toAbsolutePath().toString()

        );

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            // 2) On attend que le DOM soit complètement chargé
            wait.until(d -> ((JavascriptExecutor) d)
                .executeScript("return document.readyState")
                .equals("complete"));

            // 3) On attend l'apparition du waitedElementSelector
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(waitedElementSelector)));

            // 4) On récupère le HTML
            String html = driver.getPageSource();
            log.debug("HTML récupéré, longueur = {} caractères", html.length());

            return Jsoup.parse(html, url);

        } catch (Exception e) {
            log.error("Erreur Selenium : {}", e.getMessage(), e);
            String dump = driver.getPageSource();
            log.error("Dump HTML partiel ({} premiers chars) =\n{}",
                      Math.min(dump.length(), 2000),
                      dump.substring(0, Math.min(dump.length(), 2000)));
            throw e;
        } finally {
            driver.quit();
        }
    }
}