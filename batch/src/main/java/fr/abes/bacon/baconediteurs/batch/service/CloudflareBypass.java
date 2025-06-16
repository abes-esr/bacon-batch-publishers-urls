package fr.abes.bacon.baconediteurs.batch.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
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

    /**
     * Tente d'abord un fetch HTTP simple, sinon passe en Selenium headless
     * @param url URL de la page à récupérer
     * @param waitedCssSelector CSS selector sur lequel on attend en Selenium
     * @return Document Jsoup prêt à être parsé
     */
    public static Document fetchDocument(String url, String waitedCssSelector) throws IOException {
        try {
            // 1) Essai HTTP “pur” (JSoup)
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .timeout(30_000)
                    .get();
        } catch (Exception httpEx) {
            log.info("Bypass HTTP natif échoué ({}), passage à Selenium", httpEx.getMessage());
            // 2) Fallback Selenium
            return fetchWithSelenium(url, waitedCssSelector);
        }
    }

    /**
     * Lance un ChromeDriver headless avec un profile temporaire unique.
     */
    private static Document fetchWithSelenium(String url, String waitedCssSelector) throws IOException {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--remote-allow-origins=*",
                "--disable-blink-features=AutomationControlled",
                "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.199 Safari/537.36"
        );
        options.setBinary("/root/.cache/selenium/chrome/linux64/114.0.5735.90/chrome");
        // ON NE SPÉCIFIE PLUS --user-data-dir

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
            new WebDriverWait(driver, Duration.ofSeconds(30))
                    .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(waitedCssSelector)));
            String html = ((JavascriptExecutor) driver)
                    .executeScript("return document.documentElement.outerHTML")
                    .toString();
            return Jsoup.parse(html);
        } finally {
            // driver.quit() nettoie automatiquement le profil temporaire
            try { driver.quit(); } catch (Exception ignored) { }
        }
    }
}