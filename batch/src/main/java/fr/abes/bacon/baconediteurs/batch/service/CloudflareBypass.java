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
                "--user-data-dir=" + tempProfile.toAbsolutePath(),
                "user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
        );

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            // 1) Attendre le readyState complet
            wait.until(d -> ((JavascriptExecutor) d)
                    .executeScript("return document.readyState")
                    .equals("complete"));
            // 2) Attendre la présence d'un élément concret
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(waitedCssSelector)));

            String html = driver.getPageSource();
            log.debug("HTML Selenium récupéré ({} chars)", html.length());
            return Jsoup.parse(html, url);
        } catch (Exception seEx) {
            log.error("Erreur Selenium : {}", seEx.getMessage(), seEx);
            throw new IOException(seEx);
        } finally {
            driver.quit();  // ferme et libère le dossier tempProfile
        }
    }
}