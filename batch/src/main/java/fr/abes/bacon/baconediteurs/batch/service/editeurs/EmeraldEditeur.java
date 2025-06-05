package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.DownloadService;
import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;
import fr.abes.bacon.core.ALIAS_EDITEUR;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class EmeraldEditeur implements Editeur, Serializable {
    private final String mailAdmin;
    private final String pathToUrlsFile;
    private final String pathToRenommerFile;
    private final String emeraldDownloadUrl;
    private final String pathToFilesDownloaded;
    private final DownloadService downloadService;

    public EmeraldEditeur(String pathToUrlsFile, String pathToRenommerFile, String emeraldDownloadUrl, String pathToFilesDownloaded, String mailAdmin, DownloadService downloadService) {
        this.mailAdmin = mailAdmin;
        this.pathToUrlsFile = pathToUrlsFile.replace("editeur", getAlias().toString().toLowerCase()) + "liste_urls.txt";
        this.pathToRenommerFile = pathToRenommerFile.replace("editeur", getAlias().toString().toLowerCase()) + "renommer.txt";
        this.emeraldDownloadUrl = emeraldDownloadUrl;
        this.pathToFilesDownloaded = pathToFilesDownloaded.replace("editeur", getAlias().toString().toLowerCase());
        this.downloadService = downloadService;
    }

    @Override
    public ALIAS_EDITEUR getAlias() {
        return ALIAS_EDITEUR.EMERALD;
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
            log.error("Erreur d'accès au fichier des urls Emerald");
            throw e;
        }

        return urls;
    }

    @Override
    public void telechargementFichiers(List<String> urls) {
        try {
            Document doc = Jsoup.connect(emeraldDownloadUrl).userAgent("PostmanRuntime/7.44.0")
                    .header("accept", "*/*")
                    .timeout(10 * 1000).get();
            Elements hrefs = doc.select("a[href]");
            List<Element> listeHref = hrefs.stream().filter(url -> Objects.requireNonNull(url.attribute("href")).getValue().endsWith(".txt")).toList();
            for (String url : urls) {
                Optional<String> fileUrlOpt = listeHref.stream().filter(href -> Objects.requireNonNull(href.attribute("href")).getValue().contains(url) && !Objects.requireNonNull(href.attribute("href")).getValue().toLowerCase().contains("current")).map(href -> Objects.requireNonNull(href.attribute("href")).getValue()).findFirst();
                if (fileUrlOpt.isPresent()) {
                    String fileUrl = fileUrlOpt.get();
                    log.info("téléchargement : " + fileUrl);

                    //vérification que l'url répond
                    ResponseEntity<byte[]> response = downloadService.getRestCall(fileUrl);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        Files.write(Paths.get(pathToFilesDownloaded + fileUrl.substring(fileUrl.lastIndexOf("/") + 1)), Objects.requireNonNull(response.getBody()));
                    } else {
                        log.info("fichier non trouvé {}", fileUrl);
                    }


                } else {
                    log.error("Le fichier {} est introuvable sur le site {}", url, emeraldDownloadUrl);
                }
            }
        } catch (Exception e) {
            e.getStackTrace();
            log.error("Erreur dans la récupération des fichiers sur le site de l'éditeur Emerald : " + e.getMessage());
        }
    }

    @Override
    public void reformatFichier() {
        Path path = Paths.get(pathToRenommerFile);
        try {
            Map<String, String> mapRenommage = Files.lines(path).filter(line -> !line.isEmpty()).map(line -> line.strip().split("\\|", 2))
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),
                            parts -> parts[1].trim()
                    ));

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(pathToFilesDownloaded))) {
                stream.forEach(file -> {
                    if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".txt")) {
                        String filename = file.getFileName().toString();
                        String extractPrefix = filename.split("_(\\d{4}-\\d{2}-\\d{2}).txt")[0];
                        String newPrefix = mapRenommage.get(extractPrefix);
                        if (newPrefix != null) {
                            try {
                                File newFile = new File(file.toString().replace(extractPrefix, newPrefix).replace(".txt", ".tsv"));
                                Files.move(file, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                log.info("Renommage du fichier {} en {}", filename, newFile.getName());
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
        String requestJson = mailer.mailToJSON(mailAdmin, "Récupération des fichiers Kbart Emerald termninée", "Le téléchargement des fichiers Kbart sur le site d'Emerald s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }
}
