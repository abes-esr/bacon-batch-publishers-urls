package fr.abes.bacon.baconediteurs.batch.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class DownloadService {
    private final RestTemplate restTemplate;

    public DownloadService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Récupère une page web avec fallback automatique vers Selenium si nécessaire
     * @param url URL de la page à récupérer
     * @param waitedCssSelector CSS selector à attendre en cas d'usage de Selenium
     * @return Document Jsoup prêt à être parsé
     */
    public Document fetchDocument(String url, String waitedCssSelector) throws IOException {
        try {
            log.info("Recuperation de la page html {}", url);
            // 1) Essai HTTP "pur" (JSoup) avec en-têtes optimisés
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                    .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(30_000)
                    .followRedirects(true)
                    .get();
        } catch (Exception httpEx) {
            // 2) Fallback Selenium amélioré
            return fetchDocumentWithSelenium(url, waitedCssSelector);
        }
    }

    /**
     * Télécharge un fichier avec fallback automatique vers Selenium si nécessaire
     * @param url URL du fichier à télécharger
     * @return ResponseEntity avec le contenu du fichier
     */
    public ResponseEntity<byte[]> getRestCall(String url) throws Exception {
        log.debug("Téléchargement du fichier {}", url);

        try {
            // 1) Tentative avec RestTemplate standard
            HttpHeaders headers = createStandardHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            URI uri = UriComponentsBuilder
                    .fromHttpUrl(url)
                    .build(true)
                    .toUri();

            ResponseEntity<byte[]> response = restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);

            // Vérifier si on a reçu du contenu Cloudflare
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String beginContent = new String(response.getBody(), 0,
                        Math.min(200, response.getBody().length), StandardCharsets.UTF_8);

                if (beginContent.contains("Just a moment") ||
                        beginContent.contains("Checking your browser") ||
                        beginContent.contains("cloudflare")) {

                    log.info("Contenu Cloudflare détecté, passage à Selenium pour {}", url);
                    return downloadFileWithSelenium(url);
                }
            }

            return response;

        } catch (ResourceAccessException e){
            return downloadDocumentTrustAll(url);
        } catch (Exception e) {
            return downloadFileWithSelenium(url);
        }
    }

    public static ResponseEntity<byte[]> downloadDocumentTrustAll(String url) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        SSLSocketFactory sslSocketFactory = sc.getSocketFactory();

        Document re = Jsoup.connect(url)
                .sslSocketFactory(sslSocketFactory)
                .ignoreContentType(true)
                .parser(Parser.xmlParser())
                .timeout(10_000)
                .get();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(re.html().getBytes(StandardCharsets.UTF_8));
    }


    /**
     * Crée les en-têtes HTTP standards pour simuler un navigateur
     */
    private HttpHeaders createStandardHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, "*/*");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,fr;q=0.9,en;q=0.8");
        headers.set("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
        headers.set("Sec-Ch-Ua-Mobile", "?0");
        headers.set("Sec-Ch-Ua-Platform", "\"Windows\"");
        headers.set("Sec-Fetch-Dest", "document");
        headers.set("Sec-Fetch-Mode", "navigate");
        headers.set("Sec-Fetch-Site", "cross-site");
        headers.set("Upgrade-Insecure-Requests", "1");
        return headers;
    }

    /**
     * Lance un ChromeDriver headless avec configuration anti-détection avancée
     */
    private Document fetchDocumentWithSelenium(String url, String waitedCssSelector) throws IOException {
        ChromeOptions options = createChromeOptions();
        WebDriver driver = new ChromeDriver(options);

        try {
            return performSeleniumPageFetch(driver, url, waitedCssSelector);
        } finally {
            quitDriverSafely(driver);
        }
    }

    /**
     * Télécharge un fichier via Selenium
     */
    private ResponseEntity<byte[]> downloadFileWithSelenium(String url) {
        ChromeOptions options = createChromeOptions();
        WebDriver driver = new ChromeDriver(options);

        try {
            return performSeleniumDownload(driver, url);
        } finally {
            quitDriverSafely(driver);
        }
    }

    /**
     * Crée les options Chrome optimisées pour l'anti-détection
     */
    private ChromeOptions createChromeOptions() {
        // Désactiver les logs Selenium au niveau JVM
        System.setProperty("webdriver.chrome.silentOutput", "true");

        // Désactiver les logs spécifiques via java.util.logging
        java.util.logging.Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder").setLevel(java.util.logging.Level.OFF);
        java.util.logging.Logger.getLogger("org.openqa.selenium.chromium.ChromiumDriver").setLevel(java.util.logging.Level.SEVERE);

        ChromeOptions options = new ChromeOptions();

        // Options anti-détection avancées
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1920,1080",
                "--remote-allow-origins=*",
                "--disable-blink-features=AutomationControlled",
                "--disable-web-security",
                "--allow-running-insecure-content",
                "--disable-extensions",
                "--disable-plugins",
                "--disable-default-apps",
                "--disable-sync",
                "--disable-translate",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-features=TranslateUI",
                "--disable-ipc-flooding-protection",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-logging",
                "--disable-log-file",
                "--silent",
                "--log-level=3"
        );

        // User agent plus récent
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Préférences pour éviter la détection
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("profile.managed_default_content_settings.images", 2);
        options.setExperimentalOption("prefs", prefs);

        // Exclure les switches d'automation
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Configuration du binaire Chrome
        String chromeBin = System.getenv("CHROME_BINARY");
        if (chromeBin != null && Files.isRegularFile(Paths.get(chromeBin))) {
            options.setBinary(chromeBin);
        }

        return options;
    }

    /**
     * Effectue la récupération d'une page avec Selenium
     */
    private Document performSeleniumPageFetch(WebDriver driver, String url, String waitedCssSelector) throws IOException {
        try {
            // Scripts anti-détection
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            // Configuration des timeouts
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.get(url);

            // Attendre que Cloudflare se charge
            Thread.sleep(5000);

            // Vérifier et attendre la fin de Cloudflare
            if (driver.getPageSource().contains("Just a moment") ||
                    driver.getPageSource().contains("Checking your browser")) {
                log.info("Page Cloudflare détectée, attente supplémentaire...");
                Thread.sleep(15000);
            }

            // Attendre l'élément demandé
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(waitedCssSelector)));
            } catch (Exception e) {
                log.warn("Impossible de trouver l'élément {}, récupération du contenu actuel", waitedCssSelector);
            }

            Thread.sleep(5000);

            String html = js.executeScript("return document.documentElement.outerHTML").toString();

            if (html.contains("Just a moment") || html.contains("Checking your browser")) {
                throw new IOException("Cloudflare bloque encore l'accès après toutes les attentes");
            }

            return Jsoup.parse(html);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interruption lors de l'attente", e);
        } catch (Exception e) {
            log.error("Erreur lors de la récupération avec Selenium: {}", e.getMessage());
            throw new IOException("Impossible de récupérer la page avec Selenium", e);
        }
    }

    /**
     * Effectue le téléchargement d'un fichier avec Selenium
     */
    private ResponseEntity<byte[]> performSeleniumDownload(WebDriver driver, String url) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));

            driver.get(url);

            // Attendre le chargement
            Thread.sleep(5000);

            // Récupérer le contenu de la page
            String pageSource = driver.getPageSource();

            // Si c'est du HTML, c'est probablement une erreur
            if (pageSource.contains("<!DOCTYPE")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            pageSource = pageSource.replaceAll("\s+style=\"([^\"]*)\"" , "");
            // Sinon, traiter comme du contenu binaire
            byte[] content;
            if (pageSource.contains("<pre>")) {
                content = pageSource.substring(pageSource.lastIndexOf("<pre>") + 5, pageSource.lastIndexOf("</pre>")).getBytes(StandardCharsets.UTF_8);
            } else {
                content = pageSource.getBytes(StandardCharsets.UTF_8);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok().headers(headers).body(content);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Ferme le driver Selenium en sécurité
     */
    private void quitDriverSafely(WebDriver driver) {
        try {
            driver.quit();
        } catch (Exception e) {
            log.debug("Erreur lors de la fermeture du driver: {}", e.getMessage());
        }
    }

}
