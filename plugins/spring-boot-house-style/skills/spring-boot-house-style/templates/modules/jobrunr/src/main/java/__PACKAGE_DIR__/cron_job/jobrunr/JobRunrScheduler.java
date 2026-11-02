package __PACKAGE__.cron_job.jobrunr;

import __PACKAGE__.cron_job.CronJob;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunrScheduler {

    private final JobScheduler jobScheduler;
    private final List<CronJob> jobs;

    @PostConstruct
    void scheduleAll() {
        for (CronJob job : jobs) {
            var id = job.getIdentifier();
            jobScheduler.scheduleRecurrently(id.name(), id.getDefaultCron(), job.getZoneId(), () -> job.execute(id));
            log.info("Scheduled recurring job {} with cron '{}'", id, id.getDefaultCron());
        }
    }
}
