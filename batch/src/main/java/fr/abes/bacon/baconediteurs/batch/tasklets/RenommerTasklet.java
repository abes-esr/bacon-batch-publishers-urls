package fr.abes.bacon.baconediteurs.batch.tasklets;

import fr.abes.bacon.baconediteurs.batch.service.editeurs.ALIAS_EDITEUR;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.Editeur;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.EditeursFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public class RenommerTasklet implements Tasklet, StepExecutionListener {
    private final JobParameters jobParameters;
    private final EditeursFactory editeursFactory;
    private Editeur editeur;

    public RenommerTasklet(JobParameters jobParameters, EditeursFactory editeursFactory) {
        this.jobParameters = jobParameters;
        this.editeursFactory = editeursFactory;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.editeur = editeursFactory.getEditeur(ALIAS_EDITEUR.valueOf(jobParameters.getString("editeur")));
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.editeur.renommerFichier();
        return RepeatStatus.FINISHED;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return StepExecutionListener.super.afterStep(stepExecution);
    }
}
