package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.DownloadService;
import fr.abes.bacon.core.ALIAS_EDITEUR;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EditeursFactory {
    private final DownloadService downloadService;
    @Value("${pathToUrlsFile}")
    private String pathToUrlsFile;

    @Value("${pathToRenommerFile}")
    private String pathToRenommerFile;

    @Value("${springer.pageUrl}")
    private String springerPageUrl;

    @Value("${springer.downloadUrl}")
    private String springerDownloadUrl;

    @Value("${emerald.downloadUrl}")
    private String emeraldDownloadUrl;

    @Value("${pathToFilesDownloaded}")
    private String pathToFilesDownloaded;

    @Value("${mail.admin}")
    private String mailAdmin;

    public EditeursFactory(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    public Editeur getEditeur(ALIAS_EDITEUR alias) {
        switch (alias) {
            case SPRINGER -> {
                return new SpringerEditeur(pathToUrlsFile, pathToRenommerFile, springerPageUrl, springerDownloadUrl, pathToFilesDownloaded, mailAdmin, downloadService);
            }
            case EMERALD -> {
                return new EmeraldEditeur(pathToUrlsFile, pathToRenommerFile, emeraldDownloadUrl, pathToFilesDownloaded, mailAdmin, downloadService);
            }
            default -> throw new IllegalArgumentException("Unsupported EDITEUR " + alias);
        }
    }

}
