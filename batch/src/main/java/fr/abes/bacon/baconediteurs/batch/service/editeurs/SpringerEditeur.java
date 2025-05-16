package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.DownloadService;
import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;
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
public class SpringerEditeur implements Editeur, Serializable {
    private final String pathToUrlsFile;
    private final String pathToRenommerFile;
    private final String pageUrl;
    private final String downloadUrl;
    private final String pathToFilesDownloaded;
    private final String emailAdmin;

    private final DownloadService downloadService;

    public SpringerEditeur(String pathToUrlsFile, String pathToRenommerFile, String pageUrl, String downloadUrl, String pathToFilesDownloaded, String emailAdmin, DownloadService downloadService) {
        this.pathToUrlsFile = pathToUrlsFile.replace("editeur",getAlias().toString().toLowerCase()) + "liste_urls.txt";
        this.pathToRenommerFile = pathToRenommerFile.replace("editeur",getAlias().toString().toLowerCase())  + "renommer.txt";
        this.pageUrl = pageUrl;
        this.downloadUrl = downloadUrl;
        this.pathToFilesDownloaded = pathToFilesDownloaded.replace("editeur",getAlias().toString().toLowerCase()) ;
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
            Elements hrefs = doc.select("a[href]");
            int cpt = 0;
            List<Element> listeHref = hrefs.stream().filter(url -> Objects.requireNonNull(url.attribute("href")).getValue().startsWith("/metadata/kbart") && url.attribute("href").getValue().endsWith(".txt")).toList();
            for (String url : urls) {
                Optional<String> fileUrlOpt = listeHref.stream().filter(href -> Objects.requireNonNull(href.attribute("href")).getValue().replace("/metadata/kbart","").replaceAll("(_\\d{4}-\\d{2}-)\\d{2}.txt", "").contains(url)).map(href -> Objects.requireNonNull(href.attribute("href")).getValue()).findFirst();
                if (fileUrlOpt.isPresent()) {
                    String fileUrl = fileUrlOpt.get();
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

                } else {
                    log.error("Le fichier {} est introuvable sur le site {}", url, pageUrl);
                }
                int BATCH_SIZE = 10;
                if (cpt % BATCH_SIZE == 0) {
                    log.debug("Pause entre 2 paquets de {} téléchargement", BATCH_SIZE);
                    Thread.sleep(5000);
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
        String requestJson = mailer.mailToJSON(emailAdmin, "Récupération des fichiers Kbart Springer terminée", "Le téléchargement des fichiers Kbart sur le site de Springer s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }
}
