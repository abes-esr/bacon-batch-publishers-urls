package fr.abes.bacon.baconediteurs.batch.tasklets;

import fr.abes.bacon.core.ALIAS_EDITEUR;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.Editeur;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.EditeursFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.ArrayList;
import java.util.List;

public class TelechargementFichiersTasklet implements Tasklet, StepExecutionListener {
    private Editeur editeur;
    private List<String> listeUrls = new ArrayList<>();
    private final EditeursFactory editeursFactory;
    private final JobParameters jobParameters;

    public TelechargementFichiersTasklet(JobParameters jobParameters, EditeursFactory editeursFactory) {
        this.editeursFactory = editeursFactory;
        this.jobParameters = jobParameters;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.listeUrls = (List<String>) stepExecution.getJobExecution().getExecutionContext().get("listeUrls");
        this.editeur = editeursFactory.getEditeur(ALIAS_EDITEUR.valueOf(jobParameters.getString("editeur").toUpperCase()));
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.editeur.telechargementFichiers(this.listeUrls);
        return RepeatStatus.FINISHED;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stepExecution.getExitStatus();
    }
}
