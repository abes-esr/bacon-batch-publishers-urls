package fr.abes.bacon.baconediteurs.service.editeurs;

import fr.abes.bacon.baconediteurs.service.DownloadService;
import fr.abes.bacon.baconediteurs.service.mail.Mailer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class SpringerEditeur implements Editeurs, Serializable {
    private final String NAME = "springer";
    private final int BATCH_SIZE = 10;
    private String pathToUrlsFile;
    private String pageUrl;
    private String downloadUrl;
    private String pathToFilesDownloaded;
    private String emailAdmin;

    private DownloadService downloadService;

    public SpringerEditeur(String pathToUrlsFile, String pageUrl, String downloadUrl, String pathToFilesDownloaded, String emailAdmin, DownloadService downloadService) {
        this.pathToUrlsFile = pathToUrlsFile + NAME + File.separator + "liste_urls.txt";
        this.pageUrl = pageUrl;
        this.downloadUrl = downloadUrl;
        this.pathToFilesDownloaded = pathToFilesDownloaded + NAME + File.separator;
        this.emailAdmin = emailAdmin;
        this.downloadService = downloadService;
    }

    @Override
    public ALIAS_EDITEUR getAlias() {
        return ALIAS_EDITEUR.SPRINGER;
    }

    @Override
    public List<String> getUrls() throws IOException {
        List<String> urls = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(pathToUrlsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                urls.add(line);
            }
        } catch (IOException e) {
            log.error("Erreur d'accès au fichier des urls");
            throw e;
        }

        return urls;
    }

    @Override
    public void telechargementFichiers(List<String> urls) {
        try {
            Document doc = Jsoup.connect(pageUrl).get();
            Elements href = doc.select("a[href]");
            int cpt = 0;
            for (Element link : href) {
                String fileUrl = Objects.requireNonNull(link.attribute("href")).getValue();
                if (fileUrl.startsWith("/metadata/kbart") && fileUrl.endsWith(".txt")) {
                    if (urls.contains(fileUrl.replaceAll("/metadata/kbart/", "").replaceAll("(_\\d{4}-\\d{2}-)\\d{2}.txt", ""))) {
                        log.debug("téléchargement : " + fileUrl);

                        for (int attempt = 0; attempt < 3; attempt++) {
                            //vérification que l'url répond
                            ResponseEntity<byte[]> response = downloadService.getRestCall(downloadUrl + fileUrl);
                            if (response.getStatusCode() == HttpStatus.OK) {
                                Files.write(Paths.get(pathToFilesDownloaded + fileUrl.replaceAll("/metadata/kbart/", "")), Objects.requireNonNull(response.getBody()));
                                cpt++;
                                break;
                            } else {
                                log.info("fichier non trouvé {}", fileUrl);
                                String newUrl = fileUrl.replaceAll("(\\d{4}-\\d{2}-)\\d{2}", "$101");
                                if (!newUrl.equals(fileUrl)) {
                                    fileUrl = newUrl;
                                    Thread.sleep(2000);
                                }
                            }
                        }
                        if (cpt % BATCH_SIZE == 0) {
                            log.debug("Pause entre 2 paquets de {} téléchargement", BATCH_SIZE);
                            Thread.sleep(5000);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.getStackTrace();
            log.error("Erreur dans la récupération des fichiers sur le site de l'éditeur Springer" + e.getMessage());
        }
    }

    @Override
    public void envoiMail(Mailer mailer) {
        String requestJson = mailer.mailToJSON(emailAdmin, "Récupération des fichiers Kbart Springer termninée", "Le téléchargement des fichiers Kbart sur le site de Springer s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }
}
