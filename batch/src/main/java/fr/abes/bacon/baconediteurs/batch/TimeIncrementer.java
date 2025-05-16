package fr.abes.bacon.baconediteurs.batch;

import jakarta.annotation.Nullable;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersIncrementer;

public class TimeIncrementer implements JobParametersIncrementer {

    /**
     * Increment the time parameter with the currentTimeMillis.
     */
    @Override
    public JobParameters getNext(@Nullable JobParameters parameters) {

        JobParameters params = parameters == null ? new JobParameters() : parameters;

        String key = "time";
        return new JobParametersBuilder(params).addLong(key, System.currentTimeMillis()).toJobParameters();
    }

}
