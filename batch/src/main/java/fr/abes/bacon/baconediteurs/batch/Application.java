package fr.abes.bacon.baconediteurs.batch;

import fr.abes.bacon.baconediteurs.batch.service.editeurs.ALIAS_EDITEUR;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {

        // Extraction du paramètre --editeur=...
        String editeurArg = Arrays.stream(args)
                .filter(arg -> arg.startsWith("--editeur="))
                .map(arg -> arg.substring("--editeur=".length()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Le paramètre '--editeur=XXX' est obligatoire."));
        String logPathArg = Arrays.stream(args)
                .filter(arg -> arg.startsWith("--logPath="))
                .map(arg -> arg.substring("--logPath=".length()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Le paramètre '--logPath=XXX' est obligatoire."));
        // Validation contre l'ENUM
        ALIAS_EDITEUR editeurEnum;
        try {
            editeurEnum = ALIAS_EDITEUR.valueOf(editeurArg.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valeur de '--editeur' invalide : " + editeurArg +
                    ". Valeurs acceptées : " + Arrays.toString(ALIAS_EDITEUR.values()));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String logFileName = editeurEnum.name() + "_" + timestamp + ".log";

        ThreadContext.put("logFileName", logFileName);
        ThreadContext.put("editeur", editeurArg.toLowerCase());
        ThreadContext.put("logPath", logPathArg.toLowerCase());

        SpringApplication.exit(SpringApplication.run(Application.class, args));
    }
}
