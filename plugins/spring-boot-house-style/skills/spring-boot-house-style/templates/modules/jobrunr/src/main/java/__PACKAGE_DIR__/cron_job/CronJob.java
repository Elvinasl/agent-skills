package __PACKAGE__.cron_job;

import java.time.ZoneId;
import java.time.ZoneOffset;

public interface CronJob {

    CronIdentifier getIdentifier();

    default ZoneId getZoneId() {
        return ZoneOffset.UTC;
    }

    void execute(CronIdentifier id);
}
