# Configuration and profiles

## Profiles

`application.yaml` never sets `spring.profiles.active`; whoever starts the app chooses.

| Profile | Used for | File | Activated by |
|---|---|---|---|
| *(none)* | shared defaults, no secrets | `src/main/resources/application.yaml` | always |
| `dev` | local run against `docker-compose.yml` | `application-dev.yaml` | IDE run config or `./gradlew bootRun --args='--spring.profiles.active=dev'` |
| `remote` | **every deployed environment** (staging and prod) | `application-remote.yaml` | `SPRING_PROFILES_ACTIVE=remote` on the deployment |
| `test` | integration tests | `src/integrationTest/resources/application-test.yaml` | `@ActiveProfiles("test")` on `BaseIntegrationTest` |

- One `remote` profile serves staging and prod; they differ through environment variables only. If they genuinely need different beans, stack a second profile (`remote,staging` / `remote,prod`). Never name a deployed environment `dev` — it collides with the local profile.
- Switch implementations by environment with `@Profile("remote")` on the real adapter and `@Profile("!remote")` on the local/fake one (S3 vs MinIO, real vs logging email sender). For a code-level check use `EnvironmentChecker.isDev()` / `isRemote()`.
- An older repo may call the deployed profile something else (`aws`); keep its name there.

## What goes where

- **`application.yaml`:** defaults that are the same everywhere — server, JPA (`open-in-view: false`, `ddl-auto: validate`), Flyway flags, paging limits, messages, Rabbit retry, JobRunr, logging levels, and safe feature defaults.
- **`application-dev.yaml`:** localhost URLs on the `docker-compose.yml` ports and throwaway credentials (`postgres`/`password`, `guest`/`guest`). A real key needed locally is read from `${ENV_VAR:}`, never pasted.
- **`application-remote.yaml`:** every host, secret and pool size as `${ENV_VAR}` (with a default only when it is genuinely safe, like pool sizes). Each variable name is listed in `.env.example` without a value.
- **`application-test.yaml`:** only what tests change (disabled schedulers, fixed URLs). Database and broker connections come from Testcontainers `@ServiceConnection`.

## Properties classes

- App-wide settings bind to `config/AppProperties` (`@ConfigurationProperties("app")`, Lombok `@Getter @Setter`, nested static classes per group). `@ConfigurationPropertiesScan` on the application class registers every properties class.
- Feature settings bind to `{feature}/config/XxxProperties` under the feature's prefix (`shopify.*`, `okredo.*`).
- Prefer typed fields (`Duration`, `DataSize`, `List<String>`, `Locale`, enums) over strings, and give defaults in the field initializer.
- Inject properties classes; use `@Value("${...}")` only for a single value in a single bean.
- Feature toggles are `xxx.enabled` booleans read with `@ConditionalOnProperty` on the bean that implements the feature.

## Adding a property — checklist

1. Field on the right `XxxProperties` class (with a default if there is a sensible one).
2. Default in `application.yaml` if it is the same everywhere.
3. Local value in `application-dev.yaml`.
4. `${ENV_VAR}` in `application-remote.yaml`, and the name in `.env.example`.
5. Override in `application-test.yaml` only if tests need a different value.

## App-wide config classes (in the template)

`AppProperties`, `PersistenceConfig` (JPA auditing with the `Clock`), `TimeConfig` (UTC default zone, `Clock` bean, auditing `DateTimeProvider`), `WebMvcConfig` (CORS from `app.cors.allowed-origins`), `LocalizationConfig` (`Accept-Language` → `app.supported-locales`), `EnvironmentChecker`, `MdcCleanupFilter`, and with the `rabbit` module `config/amqp/*`.
