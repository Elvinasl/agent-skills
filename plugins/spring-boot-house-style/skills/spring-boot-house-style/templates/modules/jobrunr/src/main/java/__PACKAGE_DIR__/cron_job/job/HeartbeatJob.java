package __PACKAGE__.cron_job.job;

import __PACKAGE__.cron_job.CronIdentifier;
import __PACKAGE__.cron_job.CronJob;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HeartbeatJob implements CronJob {

    @Override
    public CronIdentifier getIdentifier() {
        return CronIdentifier.HEARTBEAT;
    }

    @Override
    @Job(name = "Heartbeat", retries = 3)
    public void execute(CronIdentifier id) {
        log.info("{} alive", id);
    }
}
