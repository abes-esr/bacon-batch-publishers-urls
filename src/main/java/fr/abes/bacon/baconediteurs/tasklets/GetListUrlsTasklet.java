package fr.abes.bacon.baconediteurs.tasklets;

import fr.abes.bacon.baconediteurs.service.editeurs.ALIAS_EDITEUR;
import fr.abes.bacon.baconediteurs.service.editeurs.Editeurs;
import fr.abes.bacon.baconediteurs.service.editeurs.EditeursFactory;
import lombok.NonNull;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetListUrlsTasklet implements Tasklet, StepExecutionListener {
    private Editeurs editeur;
    private JobParameters jobParameters;
    private List<String> listeUrls = new ArrayList<>();

    public GetListUrlsTasklet(JobParameters jobParameters) {
        this.jobParameters = jobParameters;
    }
    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        this.editeur = EditeursFactory.getEditeur(ALIAS_EDITEUR.valueOf(jobParameters.getString("editeur")));

    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {
       this.listeUrls = this.editeur.getUrls();
       return RepeatStatus.FINISHED;
    }

    @Override
    public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
        if (stepExecution.getExitStatus().equals(ExitStatus.COMPLETED)) {
            stepExecution.getJobExecution().getExecutionContext().put("listeUrls", this.listeUrls);
            stepExecution.getJobExecution().getExecutionContext().put("editeur", this.editeur);
        }
        return stepExecution.getExitStatus();
    }
}
