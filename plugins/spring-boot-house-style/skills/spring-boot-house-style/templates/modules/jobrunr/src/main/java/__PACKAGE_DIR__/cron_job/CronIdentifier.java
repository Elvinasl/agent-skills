package __PACKAGE__.cron_job;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CronIdentifier {
    HEARTBEAT("0 0 * * *"),
    ;

    private final String defaultCron;
}
