package fr.abes.bacon.baconediteurs.service.editeurs;

import fr.abes.bacon.baconediteurs.service.mail.Mailer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SpringerEditeur implements Editeurs {
    @Value("${springer.pathToUrlsFile}")
    private String pathToUrlsFile;

    @Value("${springer.downloadUrl")
    private String downloadUrl;

    @Value("${mail.admin")
    private String mailAdmin;


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
