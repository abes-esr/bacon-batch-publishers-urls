package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;

import java.io.IOException;
import java.util.List;

public interface Editeur {
      ALIAS_EDITEUR getAlias();

      List<String> getUrls() throws IOException;

      void telechargementFichiers(List<String> urls);

      void envoiMail(Mailer mailer);

      void renommerFichier();
}
