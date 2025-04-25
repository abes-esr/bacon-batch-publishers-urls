package fr.abes.bacon.baconediteurs.service.editeurs;

import fr.abes.bacon.baconediteurs.service.DownloadService;
import fr.abes.bacon.baconediteurs.service.mail.Mailer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class SpringerEditeur implements Editeurs {
    private final int BATCH_SIZE = 10;
    @Value("${springer.pathToUrlsFile}")
    private String pathToUrlsFile;

    @Value("${springer.pageUrl")
    private String pageUrl;

    @Value("${springer.downloadUrl")
    private String downloadUrl;

    @Value("${mail.admin")
    private String mailAdmin;

    @Value("${springer.pathToFilesDownloaded")
    private String pathToFilesDownloaded;

    private DownloadService downloadService;

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
            int cpt=0;
            for (Element link : href) {
                if (link.text().startsWith("/metadata/kbart") && link.text().endsWith(".txt")) {
                    String fileUrl = downloadUrl + link.text();
                    if (urls.contains(fileUrl)) {
                        log.debug("téléchargement : " + fileUrl);

                        for (int attempt = 0; attempt < 3; attempt++) {
                            //vérification que l'url répond
                            if (downloadService.checkUrl(fileUrl)) {
                                ResponseEntity<byte[]> response = downloadService.getRestCall(fileUrl);
                                if (response.getStatusCode() == HttpStatus.OK) {
                                    Files.write(Paths.get(pathToFilesDownloaded), Objects.requireNonNull(response.getBody()));
                                    cpt++;
                                    break;
                                } else {
                                    log.error("Erreur lors du téléchargement du fichier {}. Statut Http : {}", fileUrl, response.getStatusCode());
                                }
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
            log.error("Erreur dans la récupération des ficheirs sur le site de l'éditeur Springer" + e.getMessage());
        }
    }

    @Override
    public void envoiMail(Mailer mailer) {
        String requestJson=mailer.mailToJSON(mailAdmin, "Récupération des fichiers Kbart Springer termninée", "Le téléchargement des fichiers Kbart sur le site de Springer s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }
}
