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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SpringerEditeur implements Editeur, Serializable {
    private final int BATCH_SIZE = 10;
    private final String pathToUrlsFile;
    private final String pathToRenommerFile;
    private final String pageUrl;
    private final String downloadUrl;
    private final String pathToFilesDownloaded;
    private final String emailAdmin;

    private DownloadService downloadService;

    public SpringerEditeur(String pathToUrlsFile, String pathToRenommerFile, String pageUrl, String downloadUrl, String pathToFilesDownloaded, String emailAdmin, DownloadService downloadService) {
        this.pathToUrlsFile = pathToUrlsFile + getAlias().toString().toLowerCase() + File.separator + "liste_urls.txt";
        this.pathToRenommerFile = pathToRenommerFile + getAlias().toString().toLowerCase() + File.separator + "renommer.txt";
        this.pageUrl = pageUrl;
        this.downloadUrl = downloadUrl;
        this.pathToFilesDownloaded = pathToFilesDownloaded + getAlias().toString().toLowerCase() + File.separator;
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
                        log.info("téléchargement : " + fileUrl);

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
    public void renommerFichier() {
        Path path = Paths.get(pathToRenommerFile);
        try {
            Map<String, String> mapRenommage = Files.lines(path).filter(line -> !line.isEmpty()).map(line ->  line.strip().split("\\|", 2))
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),
                            parts -> parts[1].trim()
                    ));

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(pathToFilesDownloaded))) {
                stream.forEach(file -> {
                    if (Files.isRegularFile(file)) {
                        String filename = file.getFileName().toString();
                        String extractPrefix = filename.split("_(\\d{4}-\\d{2}-\\d{2}).txt")[0];
                        String newPrefix = mapRenommage.get(extractPrefix);
                        if( newPrefix != null ) {
                            try {
                                File newfile = new File(file.toString().replace(extractPrefix, newPrefix).replace(".txt", ".tsv"));
                                Files.move(file, newfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                log.info("Renommage du fichier {} en {}", filename, newfile.getName());
                            } catch (IOException e) {
                                log.error("Impossible de renommer le fichier {}", filename);
                            }
                        }

                    }
                });
            }
        } catch (IOException e) {
            log.error("Impossible d'ouvrir le fichier de renommage Springer");
        }
    }

    @Override
    public void envoiMail(Mailer mailer) {
        String requestJson = mailer.mailToJSON(emailAdmin, "Récupération des fichiers Kbart Springer termninée", "Le téléchargement des fichiers Kbart sur le site de Springer s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }
}
