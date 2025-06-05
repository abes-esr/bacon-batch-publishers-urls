package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.FtpService;
import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;
import fr.abes.bacon.core.ALIAS_EDITEUR;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Classe d'implémentation de l'interface Editeur pour gérer les fichiers KBART de l'éditeur DeGruyter.
 * <p>
 * Cette classe permet de récupérer, télécharger et traiter les fichiers KBART depuis le serveur FTP de DeGruyter.
 * Elle gère également le renommage et la fusion des fichiers selon les règles définies dans les fichiers de configuration.
 * </p>
 *
 * @see Editeur
 * @see Serializable
 * @see FtpService
 */
@Slf4j
public class DegruyterEditeur implements Editeur, Serializable {
    private final String mailAdmin;
    private final String pathToUrlsFile;
    private final String pathToRenommerFile;
    private final String pathToFusionnerFile;
    private final String degruyterHost;
    private final String degruyterUsername;
    private final String degruyterPassword;
    private final String degruyterFilePath;
    private final String pathToFilesDownloaded;
    private final FtpService ftpService;
    private final Map<String, String> datesParPrefix = new HashMap<>();

    /**
     * Constructeur de la classe DegruyterEditeur.
     * <p>
     * Initialise les chemins d'accès aux fichiers de configuration et les paramètres de connexion au serveur FTP.
     * Les chemins sont adaptés en fonction de l'alias de l'éditeur.
     * </p>
     *
     * @param pathToUrlsFile Chemin de base vers le fichier des URLs
     * @param pathToRenommerFile Chemin de base vers le fichier de renommage
     * @param pathToFusionnerFile Chemin de base vers le fichier de fusion
     * @param degruyterHost Nom d'hôte du serveur FTP DeGruyter
     * @param degruyterUsername Nom d'utilisateur pour la connexion FTP
     * @param degruyterPassword Mot de passe pour la connexion FTP
     * @param degruyterFilepath Chemin sur le serveur FTP
     * @param pathToFilesDownloaded Répertoire local pour stocker les fichiers téléchargés
     * @param mailAdmin Adresse email de l'administrateur
     * @param service Service FTP utilisé pour les opérations réseau
     */
    public DegruyterEditeur(String pathToUrlsFile, String pathToRenommerFile, String pathToFusionnerFile, String degruyterHost, String degruyterUsername, String degruyterPassword, String degruyterFilepath, String pathToFilesDownloaded, String mailAdmin, FtpService service) {
        this.pathToUrlsFile = pathToUrlsFile.replace("editeur", getAlias().toString().toLowerCase()) + "liste_urls.txt";
        this.pathToRenommerFile = pathToRenommerFile.replace("editeur", getAlias().toString().toLowerCase()) + "renommer.txt";
        this.pathToFusionnerFile = pathToFusionnerFile.replace("editeur", getAlias().toString().toLowerCase()) + "fusionner.txt";
        this.degruyterHost = degruyterHost;
        this.degruyterUsername = degruyterUsername;
        this.degruyterPassword = degruyterPassword;
        this.degruyterFilePath = degruyterFilepath;
        this.mailAdmin = mailAdmin;
        this.pathToFilesDownloaded = pathToFilesDownloaded.replace("editeur", getAlias().toString().toLowerCase());
        this.ftpService = service;
    }

    /**
     * Retourne l'alias de l'éditeur DeGruyter.
     *
     * @return Constante d'énumération représentant l'éditeur DeGruyter
     */
    @Override
    public ALIAS_EDITEUR getAlias() {
        return ALIAS_EDITEUR.DEGRUYTER;
    }

    /**
     * Récupère la liste des URLs à partir du fichier de configuration.
     * <p>
     * Cette méthode lit le fichier contenant les URLs des ressources DeGruyter
     * </p>
     *
     * @return Liste des URLs des ressources à télécharger
     * @throws IOException En cas d'erreur lors de la lecture du fichier
     */
    @Override
    public List<String> getUrls() throws IOException {
        List<String> urls = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(pathToUrlsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                urls.add(line);
            }
        } catch (IOException e) {
            log.error("Erreur d'accès au fichier des urls DeGruyter");
            throw e;
        }
        return urls;
    }

    /**
     * Télécharge les fichiers KBART depuis le serveur FTP de DeGruyter.
     * <p>
     * Cette méthode se connecte au serveur FTP, recherche les fichiers correspondant au pattern généré
     * à partir des URLs, et les télécharge dans le répertoire local. Elle identifie également les fichiers
     * manquants par rapport à la liste d'URLs attendue.
     * </p>
     *
     * @param urls Liste des URLs des ressources à télécharger
     */
    @Override
    public void telechargementFichiers(List<String> urls) {
        Pattern pattern = getPattern(urls);
        try {
            ftpService.connect(this.degruyterHost, this.degruyterUsername, this.degruyterPassword, this.degruyterFilePath);
            List<String> fichiersTrouves = ftpService.listFilesByPattern(pattern);
            Set<String> basesAttendu = new HashSet<>(urls);
            Set<String> basesTrouves = fichiersTrouves.stream()
                    .map(name -> name.substring(0, name.lastIndexOf("_")+1))
                    .collect(Collectors.toSet());

            Set<String> manquants = new HashSet<>(basesAttendu);
            manquants.removeAll(basesTrouves);
            manquants.forEach(name -> {
                log.info("Fichier non trouvé {}", name);
            });
            for (String file : fichiersTrouves) {
                log.info("téléchargement : {}", file);
                ftpService.getFile(file, this.pathToFilesDownloaded);
            }
        } catch (IOException e) {
            e.getStackTrace();
            log.error("Erreur dans la récupération des fichiers sur le serveur DeGruyter");
        } finally {
            try {
                ftpService.disconnect();
            } catch (IOException e) {
                log.error("Erreur lors de la déconnexion au serveur FTP Degruyter");
            }
        }
    }

    /**
     * Envoie un email de notification à l'administrateur pour confirmer le succès du téléchargement.
     * <p>
     * Cette méthode utilise le service Mailer pour composer et envoyer un email de confirmation
     * indiquant que le processus de téléchargement des fichiers KBART s'est terminé avec succès.
     * </p>
     *
     * @param mailer Service d'envoi d'emails
     */
    @Override
    public void envoiMail(Mailer mailer) {
        String requestJson = mailer.mailToJSON(mailAdmin, "Récupération des fichiers Kbart DeGruyter termninée", "Le téléchargement des fichiers Kbart sur le serveur d'DeGruyter s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }

    /**
     * Reformate les fichiers téléchargés selon les règles définies.
     * <p>
     * Cette méthode appelle successivement les procédures de renommage et de fusion des fichiers
     * pour adapter les fichiers téléchargés au format attendu par le système.
     * </p>
     */
    @Override
    public void reformatFichier() {
        renommerFichier();
        fusionFichier();
    }

    /**
     * Renomme les fichiers téléchargés selon les règles définies dans le fichier de renommage.
     * <p>
     * Cette méthode lit le fichier de configuration de renommage, puis parcourt tous les fichiers
     * dans le répertoire des téléchargements pour les renommer selon les règles spécifiées.
     * Elle stocke également les dates extraites des noms de fichiers pour une utilisation ultérieure
     * lors de la fusion des fichiers.
     * </p>
     */
    private void renommerFichier() {
        Path path = Paths.get(pathToRenommerFile);
        try {
            Map<String, String> mapRenommage = Files.lines(path).filter(line -> !line.isEmpty()).map(line -> line.strip().split("\\|", 2))
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),
                            parts -> parts[1].trim()
                    ));
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(pathToFilesDownloaded))) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".txt")) {
                        String filename = file.getFileName().toString();
                        String baseName = filename.substring(0,filename.lastIndexOf("_")+1);
                        String newBaseName = mapRenommage.get(baseName);
                        String date = filename.substring(filename.lastIndexOf("_")+1,filename.lastIndexOf(".txt"));
                        datesParPrefix.put(newBaseName, date);
                        try {
                            File newFile = new File(file.toString().replace(baseName, newBaseName).replace(".txt", ".tsv"));
                            Files.move(file, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            log.info("Renommage du fichier {} en {}", filename, newFile.getName());
                        } catch (IOException e) {
                            log.error("Impossible de renommer le fichier {}", filename);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Impossible d'ouvrir le fichier des urls Degruyter");
        }
    }

    /**
     * Fusionne les fichiers selon les règles définies dans le fichier de fusion.
     * <p>
     * Cette méthode lit le fichier de configuration de fusion, puis combine les fichiers spécifiés
     * en un seul fichier résultant. La fusion n'est effectuée que si au moins deux fichiers sources
     * existent et partagent la même date. Le fichier résultant conserve l'en-tête du premier fichier
     * et concatène les données de tous les fichiers sources.
     * </p>
     */
    private void fusionFichier(){
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

    /**
     * Génère un pattern d'expression régulière pour la recherche des fichiers sur le serveur FTP.
     * <p>
     * Cette méthode crée un pattern qui correspond aux fichiers KBART de DeGruyter pour le mois et l'année courants,
     * en utilisant les préfixes spécifiés dans la liste d'URLs. Le pattern correspond aux fichiers avec la structure:
     * [préfixe]_YYYY-MM-DD.txt où YYYY-MM correspond à l'année et au mois courants.
     * </p>
     *
     * @param urls Liste des URLs (préfixes) à inclure dans le pattern
     * @return Un objet Pattern compilé représentant l'expression régulière
     */
    private Pattern getPattern(List<String> urls){
        String annee = Calendar.getInstance().get(Calendar.YEAR) + "";
        String mois = Calendar.getInstance().get(Calendar.MONTH) + 1 + "";
        mois = (mois.length() == 1) ? "0" + mois : mois;
        // Construction du motif regex
        String joinedKeys = String.join("|", urls);
        String regex = "^(" + joinedKeys + ")" + annee + "-" + mois + "-\\d{2}\\.txt$";
        return Pattern.compile(regex);
    }

}
