package fr.abes.bacon.baconediteurs.batch.tasklets;

import fr.abes.bacon.baconediteurs.batch.service.editeurs.ALIAS_EDITEUR;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.Editeur;
import fr.abes.bacon.baconediteurs.batch.service.editeurs.EditeursFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class GetListUrlsTasklet implements Tasklet, StepExecutionListener {
    private Editeur editeur;
    private final JobParameters jobParameters;
    private final EditeursFactory editeursFactory;
    private List<String> listeUrls = new ArrayList<>();

    public GetListUrlsTasklet(JobParameters jobParameters, EditeursFactory editeursFactory) {
        this.jobParameters = jobParameters;
        this.editeursFactory = editeursFactory;
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        log.debug("editeur : " + jobParameters.getString("editeur"));
        this.editeur = editeursFactory.getEditeur(ALIAS_EDITEUR.valueOf(jobParameters.getString("editeur")));
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
        }
        return stepExecution.getExitStatus();
    }
}
