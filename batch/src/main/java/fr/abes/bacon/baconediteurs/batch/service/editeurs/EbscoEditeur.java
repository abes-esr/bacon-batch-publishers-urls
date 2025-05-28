package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.FtpService;
import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;
import fr.abes.bacon.core.ALIAS_EDITEUR;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class EbscoEditeur implements Editeur, Serializable {
    private final String mailAdmin;
    private final String pathToUrlsFile;
    private final String ebscoHost;
    private final String ebscoUsername;
    private final String ebscoPassword;
    private final String ebscoFilePath;
    private final String pathToFilesDownloaded;
    private final FtpService ftpService;

    public EbscoEditeur(String pathToUrlsFile, String ebscoHost, String ebscoUsername, String ebscoPassword, String ebscoFilepath, String pathToFilesDownloaded, String mailAdmin, FtpService service) {
        this.pathToUrlsFile = pathToUrlsFile.replace("editeur", getAlias().toString().toLowerCase()) + "liste_urls.txt";
        this.ebscoHost = ebscoHost;
        this.ebscoUsername = ebscoUsername;
        this.ebscoPassword = ebscoPassword;
        this.ebscoFilePath = ebscoFilepath;
        this.mailAdmin = mailAdmin;
        this.pathToFilesDownloaded = pathToFilesDownloaded.replace("editeur", getAlias().toString().toLowerCase());
        this.ftpService = service;
    }

    @Override
    public ALIAS_EDITEUR getAlias() {
        return ALIAS_EDITEUR.EBSCO;
    }

    @Override
    public List<String> getUrls() throws IOException {
        List<String> urls = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(pathToUrlsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                urls.add(line.split("\\|")[0]);
            }
        } catch (IOException e) {
            log.error("Erreur d'accès au fichier des urls Ebsco");
            throw e;
        }

        return urls;
    }

    @Override
    public void telechargementFichiers(List<String> urls) {
        try {
            ftpService.connect(this.ebscoHost, this.ebscoUsername, this.ebscoPassword, this.ebscoFilePath);
            for (String url : urls) {
                log.info("téléchargement : {}", url);
                if (!ftpService.getFile(url, this.pathToFilesDownloaded)) {
                    log.info("Fichier non trouvé {}", url);
                }
            }
            ftpService.disconnect();
        } catch (IOException e) {
            e.getStackTrace();
            log.error("Erreur dans la récupération des fichiers sur le serveur Ebsco");
        }
    }

    @Override
    public void envoiMail(Mailer mailer) {
        String requestJson = mailer.mailToJSON(mailAdmin, "Récupération des fichiers Kbart Ebsco termninée", "Le téléchargement des fichiers Kbart sur le serveur d'Ebsco s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }

    @Override
    public void renommerFichier() {
        Path path = Paths.get(pathToUrlsFile);
        try {
            ftpService.connect(ebscoHost, ebscoUsername, ebscoPassword, ebscoFilePath);
            Map<String, String> mapRenommage = Files.lines(path).filter(line -> !line.isEmpty()).map(line -> line.strip().split("\\|", 2))
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),
                            parts -> parts[1].trim()
                    ));
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(pathToFilesDownloaded))) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".txt")) {
                        String filename = file.getFileName().toString();
                        String dateFichier = ftpService.getLastModificationDate(filename);
                        String newFilename = mapRenommage.get(filename);
                        String newFilenameWithDate = newFilename.replace("AAAA-MM-JJ", dateFichier);
                        try {
                            File newFile = new File(file.toString().replace(filename, newFilenameWithDate).replace(".txt", ".tsv"));
                            Files.move(file, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            log.info("Renommage du fichier {} en {}", filename, newFile.getName());
                        } catch (IOException e) {
                            log.error("Impossible de renommer le fichier {}", filename);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Impossible d'ouvrir le fichier des urls Ebsco");
        } finally {
            try {
                ftpService.disconnect();
            } catch (IOException e) {
                log.error("Erreur lors de la déconnexion au serveur FTP Ebsco");
            }
        }
    }
}
