# Scheduled jobs (JobRunr)

Use JobRunr for anything on a schedule: it is distributed, so with several instances a recurring job still runs once. Use `@Scheduled` only for per-instance housekeeping.

The `jobrunr` scaffold module adds `cron_job/CronJob.java`, `CronIdentifier.java`, `jobrunr/JobRunrScheduler.java` and an example `job/HeartbeatJob.java` (delete it once you have a real job).

## Adding a job

1. Add a value to `CronIdentifier` with its cron. JobRunr accepts 5-field (`"0 3 * * *"` = 03:00 daily) and 6-field crons with seconds.
2. Add `cron_job/job/{Name}Job implements CronJob`, annotated `@Job(name = "...", retries = 3)` on `execute`. It delegates to a service and holds no logic itself.
3. `JobRunrScheduler` registers every `CronJob` bean at startup, using the identifier name as the recurring-job id. Renaming an identifier creates a new job, so delete the old one in the dashboard.

## Details

- **Time zone:** `CronJob.getZoneId()` defaults to UTC. Override it on jobs tied to a business-local time ("09:00 in Vilnius").
- **Server:** `jobrunr.background-job-server.enabled: true`, `worker-count: 5`, `thread-type: VirtualThreads`. The dashboard runs on port 9091; disable it in `application-remote.yaml` if it must not be reachable, or protect it with `jobrunr.dashboard.username/password`.
- **Integration tests** disable the background server and dashboard (`application-test.yaml`), so jobs don't fire mid-test.
- **Fire-and-forget work** (send an email after commit, recompute stats): `jobScheduler.enqueue(() -> service.method(id))`. Pass ids, not entities — arguments are serialised.
- **Database pool:** JobRunr shares the main datasource. If the pool is tight, define a small dedicated `DataSource` bean and point `jobrunr.database.datasource` at its bean name.
