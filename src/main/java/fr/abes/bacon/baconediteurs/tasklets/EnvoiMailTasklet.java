package fr.abes.bacon.baconediteurs.tasklets;

import fr.abes.bacon.baconediteurs.service.editeurs.Editeurs;
import fr.abes.bacon.baconediteurs.service.mail.Mailer;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public class EnvoiMailTasklet implements Tasklet, StepExecutionListener {
    private Editeurs editeur;
    private Mailer mailer;

    public EnvoiMailTasklet(Mailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.editeur = (Editeurs) stepExecution.getJobExecution().getExecutionContext().get("editeur");
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
