package fr.abes.bacon.baconediteurs.batch;

import fr.abes.bacon.baconediteurs.batch.service.editeurs.EditeursFactory;
import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;
import fr.abes.bacon.baconediteurs.batch.tasklets.EnvoiMailTasklet;
import fr.abes.bacon.baconediteurs.batch.tasklets.GetListUrlsTasklet;
import fr.abes.bacon.baconediteurs.batch.tasklets.RenommerTasklet;
import fr.abes.bacon.baconediteurs.batch.tasklets.TelechargementFichiersTasklet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class JobConfiguration {
    private final ApplicationArguments applicationArguments;
    private final EditeursFactory editeursFactory;
    private final Mailer mailer;

    public JobConfiguration(ApplicationArguments applicationArguments, EditeursFactory editeursFactory, Mailer mailer) {
        this.applicationArguments = applicationArguments;
        this.editeursFactory = editeursFactory;
        this.mailer = mailer;
    }

    @Bean
    public Tasklet getListUrlsTasklet() {
        return new GetListUrlsTasklet(jobParameters(), editeursFactory);
    }

    @Bean
    public Tasklet telechargementFichiersTasklet() {
        return new TelechargementFichiersTasklet(jobParameters(), editeursFactory);
    }

    @Bean
    public Tasklet renommerTasklet() {
        return new RenommerTasklet(jobParameters(), editeursFactory);
    }

    @Bean
    public Tasklet envoiMailTasklet() {
        return new EnvoiMailTasklet(jobParameters(), editeursFactory, mailer);
    }

    @Bean
    public Step stepGetListUrls(JobRepository jobRepository, @Qualifier("getListUrlsTasklet")Tasklet getListUrlsTasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("stepLectureMotsCles", jobRepository).allowStartIfComplete(true)
                .tasklet(getListUrlsTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepTelechargementFichiers(JobRepository jobRepository, @Qualifier("telechargementFichiersTasklet")Tasklet telechargementFichiersTasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("stepTelechargementFichiers", jobRepository).allowStartIfComplete(true)
                .tasklet(telechargementFichiersTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step stepRenommer(JobRepository jobRepository, @Qualifier("renommerTasklet")Tasklet renommerTasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("stepRenommer", jobRepository).allowStartIfComplete(true)
                .tasklet(renommerTasklet, transactionManager).build();
    }

    @Bean
    public Step stepEnvoiMail(JobRepository jobRepository, @Qualifier("envoiMailTasklet") Tasklet envoiMailTasklet, PlatformTransactionManager transactionManager) {
        return new StepBuilder("stepEnvoiMail", jobRepository).allowStartIfComplete(true)
                .tasklet(envoiMailTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job jobRecuperationKbart(JobRepository jobRepository, @Qualifier("stepGetListUrls")Step stepGetListUrls, @Qualifier("stepTelechargementFichiers")Step stepTelechargementFichiers, @Qualifier("stepRenommer")Step stepRenommer, @Qualifier("stepEnvoiMail")Step stepEnvoiMail) {
        return new JobBuilder("jobRecuperationKbart", jobRepository).incrementer(incrementer())
                .start(stepGetListUrls).next(stepTelechargementFichiers).next(stepRenommer).next(stepEnvoiMail)
                .build();
    }

    protected JobParametersIncrementer incrementer() {
        return new TimeIncrementer();
    }

    @Bean
    public JobParameters jobParameters() {
        Map<String, JobParameter<?>> params = new HashMap<>();
        if (applicationArguments.containsOption("editeur")) {
            List<String> values = applicationArguments.getOptionValues("editeur");
            if (values != null && !values.isEmpty()) {
                String editeur = values.get(0);
                log.debug("Appel du batch pour l'éditeur : {}", editeur);
                params.put("editeur", new JobParameter<>(editeur, String.class));
            }
        }
        return new JobParameters(params);
    }
}
