package fr.abes.bacon.baconediteurs.tasklets;

import fr.abes.bacon.baconediteurs.service.editeurs.Editeurs;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.ArrayList;
import java.util.List;

public class TelechargementFichiersTasklet implements Tasklet, StepExecutionListener {
    private Editeurs editeur;
    private List<String> listeUrls = new ArrayList<>();

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.listeUrls = (List<String>) stepExecution.getJobExecution().getExecutionContext().get("listeUrls");
        this.editeur = (Editeurs) stepExecution.getJobExecution().getExecutionContext().get("editeur");
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        this.editeur.telechargementFichiers(this.listeUrls);
        return RepeatStatus.FINISHED;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getExitStatus().equals(ExitStatus.COMPLETED)) {
            stepExecution.getJobExecution().getExecutionContext().put("editeur", this.editeur);
        }
        return stepExecution.getExitStatus();
    }
}
