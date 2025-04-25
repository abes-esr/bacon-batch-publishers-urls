package fr.abes.bacon.baconediteurs.service.editeurs;

import fr.abes.bacon.baconediteurs.service.mail.Mailer;

import java.io.IOException;
import java.util.List;

public interface Editeurs {
      ALIAS_EDITEUR getAlias();

      List<String> getUrls() throws IOException;

      void telechargementFichiers(List<String> urls);

      void envoiMail(Mailer mailer);
}
