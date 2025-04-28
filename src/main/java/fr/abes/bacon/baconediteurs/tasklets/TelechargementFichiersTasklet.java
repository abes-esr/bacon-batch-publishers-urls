package fr.abes.bacon.baconediteurs.tasklets;

import fr.abes.bacon.baconediteurs.service.editeurs.ALIAS_EDITEUR;
import fr.abes.bacon.baconediteurs.service.editeurs.Editeurs;
import fr.abes.bacon.baconediteurs.service.editeurs.EditeursFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.ArrayList;
import java.util.List;

public class TelechargementFichiersTasklet implements Tasklet, StepExecutionListener {
    private Editeurs editeur;
    private List<String> listeUrls = new ArrayList<>();
    private EditeursFactory editeursFactory;
    private JobParameters jobParameters;

    public TelechargementFichiersTasklet(JobParameters jobParameters, EditeursFactory editeursFactory) {
        this.editeursFactory = editeursFactory;
        this.jobParameters = jobParameters;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.listeUrls = (List<String>) stepExecution.getJobExecution().getExecutionContext().get("listeUrls");
        this.editeur = editeursFactory.getEditeur(ALIAS_EDITEUR.valueOf(jobParameters.getString("editeur")));
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
