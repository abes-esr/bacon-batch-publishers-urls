package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.DownloadService;
import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;
import fr.abes.bacon.core.ALIAS_EDITEUR;
import lombok.extern.slf4j.Slf4j;
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
public class KargerEditeur implements Editeur, Serializable {
    private final String pathToUrlsFile;
    private final String pathToRenommerFile;
    private final String pathToFusionnerFile;
    private final String pageUrl;
    private final String downloadUrl;
    private final String pathToFilesDownloaded;
    private final String emailAdmin;
    private final Map<String, String> datesParPrefix = new HashMap<>();

    private final DownloadService downloadService;

    public KargerEditeur(String pathToUrlsFile, String pathToRenommerFile, String pathtoFusionnerFile, String pageUrl, String downloadUrl, String pathToFilesDownloaded, String emailAdmin, DownloadService downloadService) {
        this.pathToUrlsFile = pathToUrlsFile.replace("editeur",getAlias().toString().toLowerCase()) + "liste_urls.txt";
        this.pathToRenommerFile = pathToRenommerFile.replace("editeur",getAlias().toString().toLowerCase())  + "renommer.txt";
        this.pathToFusionnerFile= pathtoFusionnerFile.replace("editeur",getAlias().toString().toLowerCase())  + "fusionner.txt";
        this.pageUrl = pageUrl;
        this.downloadUrl = downloadUrl;
        this.pathToFilesDownloaded = pathToFilesDownloaded.replace("editeur",getAlias().toString().toLowerCase()) ;
        this.emailAdmin = emailAdmin;
        this.downloadService = downloadService;
    }

    @Override
    public ALIAS_EDITEUR getAlias() {
        return ALIAS_EDITEUR.KARGER;
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
            log.error("Erreur d'accès au fichier des urls Karger");
            throw e;
        }

        return urls;
    }

    @Override
    public void telechargementFichiers(List<String> urls) {
        try {
            Document doc = downloadService.fetchDocument(pageUrl,"ul");
            Elements hrefs = doc.select("a[href]");
            int cpt = 1;
            List<Element> listeHref = hrefs.stream().filter(url -> Objects.requireNonNull(url.attribute("href")).getValue().startsWith("/DocumentLibrary/") && url.attribute("href").getValue().endsWith(".txt")).toList();
            for (String url : urls) {
                Optional<String> fileUrlOpt = listeHref.stream().filter(href -> Objects.requireNonNull(href.attribute("href")).getValue().contains(url)).map(href -> Objects.requireNonNull(href.attribute("href")).getValue()).findFirst();
                if (fileUrlOpt.isPresent()) {
                    String fileUrl = fileUrlOpt.get();
                    for (int attempt = 0; attempt < 3; attempt++) {
                        //vérification que l'url répond
                        ResponseEntity<byte[]> response = downloadService.getRestCall(downloadUrl + fileUrl);
                        if (response.getStatusCode() == HttpStatus.OK) {
                            Files.write(Paths.get(pathToFilesDownloaded + fileUrl.substring(fileUrl.lastIndexOf("/"))), Objects.requireNonNull(response.getBody()));
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
            log.error("Erreur dans la récupération des fichiers sur le site de l'éditeur Karger " + e.getMessage());
        }
    }

    @Override
    public void reformatFichier() {
        renommerFichier();
        fusionFichier();
    }

    @Override
    public void envoiMail(Mailer mailer) {
        String requestJson = mailer.mailToJSON(emailAdmin, "Récupération des fichiers Kbart Karger terminée", "Le téléchargement des fichiers Kbart sur le site de Karger s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }

    private void renommerFichier() {
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
                        String extractPrefix = filename.split("_(\\d{4}-\\d{2}-\\d{2})")[0];
                        String extractDate = filename.replaceAll(".*_(\\d{4}-\\d{2}-\\d{2}).*", "$1");
                        String newPrefix = mapRenommage.get(extractPrefix);
                        datesParPrefix.put(newPrefix + "_", extractDate);
                        if( newPrefix != null ) {
                            try {
                                File newFile = new File(pathToFilesDownloaded + newPrefix + "_" + extractDate + ".tsv");
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
            log.error("Impossible d'ouvrir le fichier de renommage Karger");
        }
    }

    private void fusionFichier() {
        Path path = Paths.get(pathToFusionnerFile);
        try {
            Map<String, List<String>> mapFusionner = Files.lines(path)
                    .filter(line -> !line.isBlank())
                    .map(line -> line.strip().split("\\|", 2))
                    .collect(Collectors.toMap(
                            parts -> parts[1].trim(), // clé = fichier fusionné
                            parts -> Arrays.stream(parts[0].split(","))
                                    .map(String::trim)
                                    .collect(Collectors.toList())
                    ));

            for (Map.Entry<String, List<String>> entry : mapFusionner.entrySet()) {
                String prefixFusion = entry.getKey();
                List<String> prefixesSources = entry.getValue();

                List<Path> fichiersAScanner = new ArrayList<>();
                Set<String> dates = new HashSet<>();

                for (String prefix : prefixesSources) {
                    String date = datesParPrefix.get(prefix);

                    if (!(date == null)) {
                        Path fichier = Paths.get(pathToFilesDownloaded, prefix + date + ".tsv");
                        if (Files.exists(fichier)) {
                            fichiersAScanner.add(fichier);
                            dates.add(date);
                        } else {
                            log.warn("Fichier manquant pour le préfixe : {}", prefix);
                        }
                    }
                }

                boolean suffisammentDeFichiers = fichiersAScanner.size() >= 2;
                boolean uneSeuleDateCommune = dates.size() == 1;

                if (suffisammentDeFichiers && uneSeuleDateCommune) {
                    String dateFinale = dates.iterator().next();
                    Path fichierSortie = Paths.get(pathToFilesDownloaded, prefixFusion + dateFinale + ".tsv");

                    try (BufferedWriter writer = Files.newBufferedWriter(fichierSortie, StandardCharsets.UTF_8)) {
                        boolean headerEcrit = false;

                        for (Path fichier : fichiersAScanner) {
                            try (BufferedReader reader = Files.newBufferedReader(fichier, StandardCharsets.UTF_8)) {
                                String ligne;
                                boolean premiereLigne = true;

                                while ((ligne = reader.readLine()) != null) {
                                    if (!premiereLigne || !headerEcrit) {
                                        writer.write(ligne);
                                        writer.newLine();
                                        headerEcrit = true;
                                    }
                                    premiereLigne = false;
                                }
                            }
                        }
                        writer.flush();
                        log.info("Fichier fusionné généré : {}", fichierSortie.getFileName());
                    } catch (IOException e) {
                        log.error("Erreur pendant la fusion du fichier {} : {}", fichierSortie.getFileName(), e.getMessage());
                    }
                } else {
                    if (!suffisammentDeFichiers) {
                        log.debug("Fusion ignorée pour {} : moins de 2 fichiers valides.", prefixFusion);
                    } else {
                        log.debug("Fusion ignorée pour {} : fichiers de dates différentes : {}", prefixFusion, dates);
                    }
                    log.info("Fichier fusionné ignoré : {}", prefixFusion);
                }
            }
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier de fusion : {}", e.getMessage());
        }
    }
}
