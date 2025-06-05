package fr.abes.bacon.baconediteurs.batch.service.editeurs;

import fr.abes.bacon.baconediteurs.batch.service.DownloadService;
import fr.abes.bacon.baconediteurs.batch.service.FtpService;
import fr.abes.bacon.core.ALIAS_EDITEUR;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EditeursFactory {
    private final DownloadService downloadService;
    private final FtpService ftpService;
    @Value("${pathToUrlsFile}")
    private String pathToUrlsFile;

    @Value("${pathToRenommerFile}")
    private String pathToRenommerFile;

    @Value("${pathToFusionnerFile}")
    private String pathToFusionnerFile;

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

    @Value("${ebsco.hostname}")
    private String ebscoHost;
    @Value("${ebsco.username}")
    private String ebscoUsername;
    @Value("${ebsco.password}")
    private String ebscoPassword;
    @Value("${ebsco.filepath}")
    private String ebscoFilepath;

    @Value("${degruyter.hostname}")
    private String degruyterHost;
    @Value("${degruyter.username}")
    private String degruyterUsername;
    @Value("${degruyter.password}")
    private String degruyterPassword;
    @Value("${degruyter.filepath}")
    private String degruyterFilepath;

    public EditeursFactory(DownloadService downloadService, FtpService ftpService) {
        this.downloadService = downloadService;
        this.ftpService = ftpService;
    }

    public Editeur getEditeur(ALIAS_EDITEUR alias) {
        switch (alias) {
            case SPRINGER -> {
                return new SpringerEditeur(pathToUrlsFile, pathToRenommerFile, springerPageUrl, springerDownloadUrl, pathToFilesDownloaded, mailAdmin, downloadService);
            }
            case EMERALD -> {
                return new EmeraldEditeur(pathToUrlsFile, pathToRenommerFile, emeraldDownloadUrl, pathToFilesDownloaded, mailAdmin, downloadService);
            }
            case EBSCO -> {
                return new EbscoEditeur(pathToUrlsFile, ebscoHost, ebscoUsername, ebscoPassword, ebscoFilepath, pathToFilesDownloaded, mailAdmin, ftpService);
            }
            case DEGRUYTER -> {
                return new DegruyterEditeur(pathToUrlsFile, pathToRenommerFile, pathToFusionnerFile, degruyterHost, degruyterUsername, degruyterPassword, degruyterFilepath, pathToFilesDownloaded, mailAdmin, ftpService);
            }
            default -> throw new IllegalArgumentException("Unsupported EDITEUR " + alias);
        }
    }

}
