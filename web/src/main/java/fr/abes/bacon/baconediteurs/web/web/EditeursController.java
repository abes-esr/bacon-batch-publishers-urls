package fr.abes.bacon.baconediteurs.web.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1")
@Slf4j
public class EditeursController {
    @GetMapping(value = "/launchBatchEditeur/{editeur}")
    public String launchBatchEditeur(@PathVariable("editeur") String editeur) {
        log.info(editeur);
        return editeur;
    }

}
