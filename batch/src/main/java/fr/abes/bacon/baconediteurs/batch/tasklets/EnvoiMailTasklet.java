package fr.abes.bacon.baconediteurs.batch.tasklets;

import fr.abes.bacon.core.ALIAS_EDITEUR;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.Editeur;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.EditeursFactory;
import fr.abes.bacon.baconediteurs.batch.service.mail.Mailer;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public class EnvoiMailTasklet implements Tasklet, StepExecutionListener {
    private Editeur editeur;
    private final Mailer mailer;
    private final JobParameters jobParameters;
    private final EditeursFactory editeursFactory;

    public EnvoiMailTasklet(JobParameters jobParameters, EditeursFactory editeursFactory, Mailer mailer) {
        this.jobParameters = jobParameters;
        this.editeursFactory = editeursFactory;
        this.mailer = mailer;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.editeur = editeursFactory.getEditeur(ALIAS_EDITEUR.valueOf(jobParameters.getString("editeur").toUpperCase()));
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.editeur.envoiMail(mailer);
        return RepeatStatus.FINISHED;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return StepExecutionListener.super.afterStep(stepExecution);
    }
}
