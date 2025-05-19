package fr.abes.bacon.baconediteurs.web.web;

import fr.abes.bacon.baconediteurs.batch.service.editeurs.ALIAS_EDITEUR;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1")
@Slf4j
public class EditeursController {

    @Value("${batch.jar.path}")
    private String batchJarPath;


    @GetMapping(value = "/launchBatchEditeur/{editeur}")
    public ResponseEntity launchBatchEditeur(@PathVariable("editeur") String editeur) {

        try {
            ALIAS_EDITEUR editeurEnum = ALIAS_EDITEUR.valueOf(editeur.toUpperCase());
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", batchJarPath, "--editeur=" + editeurEnum.name().toLowerCase()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Lecture des logs du batch en asynchrone
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("[BATCH-{}] {}", editeurEnum.name(), line);
                    }
                } catch (IOException e) {
                    log.warn("Erreur lors de la lecture des logs du batch", e);
                }
            }).start();

            // Réponse immédiate
            return ResponseEntity.accepted().body(
                    "Batch lancé pour l’éditeur : " + editeurEnum.name().toLowerCase() + "." +
                            " L'exécution se poursuit en arrière-plan. Consultez les logs batch pour le suivi."
            );

        } catch (IOException e) {
            log.error("Erreur lors du lancement du batch", e);
            return ResponseEntity.internalServerError().body("Erreur : " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    "Éditeur invalide : '" + editeur + "'. " +
                            "Valeurs acceptées : " + Arrays.toString(ALIAS_EDITEUR.values())
            );
        }
    }

}
