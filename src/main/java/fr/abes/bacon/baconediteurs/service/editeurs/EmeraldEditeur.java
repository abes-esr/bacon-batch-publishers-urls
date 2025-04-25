package fr.abes.bacon.baconediteurs.service.editeurs;

import fr.abes.bacon.baconediteurs.service.mail.Mailer;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

public class EmeraldEditeur implements Editeurs {
    @Value("${mail.admin")
    private String mailAdmin;

    @Override
    public ALIAS_EDITEUR getAlias() {
        return ALIAS_EDITEUR.EMERALD;
    }

    @Override
    public List<String> getUrls() {
        return List.of();
    }

    @Override
    public void telechargementFichiers(List<String> urls) {

    }

    @Override
    public void envoiMail(Mailer mailer) {
        String requestJson=mailer.mailToJSON(mailAdmin, "Récupération des fichiers Kbart Emerald termninée", "Le téléchargement des fichiers Kbart sur le site d'Emerald s'est terminé avec succès !");
        mailer.sendMail(requestJson);
    }
}
