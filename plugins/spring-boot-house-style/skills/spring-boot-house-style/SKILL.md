---
name: spring-boot-house-style
description: Use when creating or extending any Java/Spring Boot project (scaffolding, entities, Flyway, Lombok, profiles, RabbitMQ, JobRunr, observability, Gradle, Docker) so it follows this house style.
---

# Spring Boot house style

These conventions were distilled from three production Spring Boot services. Use them when you scaffold a new service or add code to an existing one.

In an existing repo, the repo's own conventions win: match what is already there, and read the repo's CLAUDE.md first. In a new repo, follow this file.

## 0. Before you start

- **New project:** ask which database to use, **PostgreSQL** or **MySQL**. Don't assume.
- **Also confirm:** app name, reverse domain, and which optional modules are needed (RabbitMQ, JobRunr, security/JWT). Observability and Sentry are on by default.
- **Existing project:** look at a few neighbours of what you're about to add (one entity, one repository, one service, one controller, the newest migration) and copy them.

## 1. Code style

- Keep comments to a minimum. Name things and structure the code so it explains itself.
- Write a comment only when the WHY is genuinely non-obvious: a hidden constraint, a workaround, a race condition, a wire contract with another service. Never restate what the code says.
- When a comment is needed, prefer Javadoc on the class or method over inline `//` comments.
- Use `var` for locals when the type is obvious from the right-hand side.
- Use constructor injection only, through Lombok (section 6). Never `@Autowired`.
- Prefer `Optional` + `.orElseThrow(() -> new XxxNotFoundException(...))` over null checks.
- Use Java 21 features freely: records, text blocks for JPQL/SQL/GraphQL, pattern matching (`if (e instanceof Foo foo)`), `.toList()`.
- Use `Instant` for all timestamps, and keep the app in UTC everywhere.
- Never commit real secrets (section 15).

## 2. Git

- Commit messages are one short line, lowercase, imperative: `add vat amount edit request`, `prevent duplicate seats on concurrent joins`.
- No `fix:`/`feat:` prefixes, no body, no Co-Authored-By trailer.
- Commit or push only when asked.

## 3. Java package layout

**Root package:** `{reverse-domain}.{appname}`, with dashes removed and all lowercase. Examples: `com.couchclues.couchcluescore`, `ai.blokai.invoiceparser`.

**Main class:** `{AppName}Application`, with a plain `@SpringBootApplication` and nothing else on it.

Packages are organised **by feature, not by layer**. Feature package names are lowercase `snake_case` (`room_member`, `game_order`, `document_type`). Each feature has the same sub-packages:

```
{root}/
  {App}Application.java
  OkController.java              # trivial GET "/" ping
  amqp/AMQPDefs.java             # exchanges, queues, DLQs (if RabbitMQ)
  config/                        # global config classes (section 15)
    amqp/                        #   RabbitConfig, LoggingRejectAndDontRequeueRecoverer, MdcCleanupInterceptor
  exception/                     # shared exceptions
    handlers/                    # @RestControllerAdvice classes + ExceptionLogger
  model/                         # shared enums/value types, ErrorResponse, ErrorId
  cron_job/                      # CronJob, CronIdentifier, jobrunr/JobRunrScheduler, job/*Job
  util/                          # stateless XxxUtil / XxxUtils helpers
  {feature}/
    controller/                  # XxxController
      dto/                       # XxxRequest, XxxResponse
    jpa/                         # entities + repositories
      event/                     # Spring application events (records)
    service/                     # business logic
    model/                       # feature-internal value types, enums, XxxView
    amqp/                        # XxxEventHandler (consumer), XxxEventSender (producer)
      event/                     # XxxEvent payloads
    exception/                   # feature-specific exceptions + handler (optional)
    config/                      # feature-specific XxxProperties and HTTP client beans
```

## 4. Where files live

```
{repo}/
  build.gradle, settings.gradle, gradlew, gradle/wrapper/
  Dockerfile
  docker-compose.yml                  # LOCAL dev only: db + rabbitmq
  docker_postgres_init_{app}.sql      # CREATE DATABASE {app}_dev
  .env.example                        # names of env vars the remote profile needs, no values
  .gitignore                          # Spring Initializr default + .docker/ .idea build/ .gradle
  CLAUDE.md                           # code style + git rules (sections 1-2) + repo-specific notes
  README.md
  docs/                               # optional: event contracts, runbooks
  .github/
    workflows/deploy-staging.yml, deploy-master.yml, deploy-infra-*.yml, deploy-obs.yml
    actions/slack-notify/action.yml
  infrastructure/{server}/            # what runs on each server (section 19)
    docker-compose.yml                #   app containers + traefik, env vars, memory limits
    traefik-tls.yml
    alloy-config.alloy                #   ships this server's metrics/logs/traces (section 16)
  observability/                      # central Grafana stack (section 16)
  src/main/resources/
    application.yaml                  # shared defaults, no secrets
    application-dev.yaml              # local profile
    application-remote.yaml           # deployed profile, every secret/host is ${ENV_VAR}
    application-test.yaml             # integration-test profile
    application-observability.yml     # management/metrics/tracing, imported by remote
    logback-spring.xml                # optional: only when MDC must appear in the log pattern
    db/migration/                     # Flyway V__ and R__ scripts
    i18n/messages.properties          # + messages_{lang}.properties, if localised
    templates/, static/, schema/      # only when used
  src/test/java/...                   # unit tests, same package as the class under test
  src/integrationTest/java/...        # *IT tests
  src/integrationTest/resources/docker-compose-test.yml
```

**Rule of thumb for config classes:**
- If it configures the whole app (persistence, time, CORS, Rabbit, async, profile checks), it goes in `{root}/config`.
- If it belongs to one integration or feature (Shopify, S3, email, an API client), it goes in `{feature}/config`.

## 5. Naming

| Thing | Convention | Example |
|---|---|---|
| JPA entity | Domain noun, **no `Entity` suffix** | `DocumentType`, `RoomMember`, `CompanyDocumentTypeConfig` |
| Repository | `{Entity}Repository` | `RoomMemberRepository` |
| Table | plural `snake_case` | `room_members`, `company_document_type_configs` |
| Column | `snake_case`; booleans `is_x`; timestamps `x_at` | `is_guest`, `kicked_at` |
| Request/response DTOs | `XxxRequest` / `XxxResponse` in `controller/dto` | `SetMemberNameRequest` |
| Read models sent to the UI | `XxxView` in `model` | `PuzzleStateView` |
| Exceptions | `XxxNotFoundException`, `XxxAlreadyExistsException`, `XxxIsFullException`… | `RoomNotFoundException` |
| AMQP events | Facts in past tense `XxxCreatedEvent`/`XxxRequestedEvent`/`XxxFetchedEvent`; commands `FetchXxxEvent` | `ShopifyGameOrderBatchCreatedEvent` |
| Services | Named for their single role | `PuzzleRestarter`, `PuzzleFinder`, `PuzzleSaver`, `PuzzleResultCalculator`, `XxxMapper`, `XxxNotifier`, `XxxProcessor`, `XxxResolver`, `XxxChecker`, `XxxValidator`, `XxxClient` |
| Broad façades | `XxxService` | `RoomMemberService` |
| Config properties class | `XxxProperties` (global one: `AppConfig`/`AppProperties`) | `ShopifyWebhookProperties` |
| REST paths | plural kebab-case, nested by ownership | `/document-type-configs`, `/v2/game/rooms/{roomId}/members/{memberId}/name` |
| Config keys | kebab-case under a feature prefix | `app.cors.allowed.origins`, `managed-links.daily-limit` |
| Queues / routing keys | `{service}.{feature}.{event}` / `snake_case` | `core.game_order.shopify_batch_requested` |
| Containers | `{app}-{env}` | `couchclues-core-api-prod` |

Prefer several small, single-purpose services over one god-service. When a service grows past a few responsibilities, split out a `PuzzleSnapshotMaker`- or `PuzzleDifficultyChanger`-style class.

## 6. Lombok

Lombok is used everywhere. Don't hand-write getters, setters, constructors, builders or loggers.

**Gradle setup:**

```groovy
configurations { compileOnly { extendsFrom annotationProcessor } }

compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
testCompileOnly 'org.projectlombok:lombok'
testAnnotationProcessor 'org.projectlombok:lombok'
// with an integrationTest source set, also:
integrationTestCompileOnly 'org.projectlombok:lombok'
integrationTestAnnotationProcessor 'org.projectlombok:lombok'
```

**Which annotations go where:**

| Kind of class | Annotations |
|---|---|
| Spring beans (`@Service`, `@Component`, `@RestController`, `@Configuration`) | `@RequiredArgsConstructor` + `private final` fields; `@Slf4j` if it logs |
| JPA entity | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` |
| Request DTO | `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` (+ `@Builder` if tests or code build it) |
| Response DTO / `XxxView` | `@Data @AllArgsConstructor`, or `@Data @Builder @NoArgsConstructor @AllArgsConstructor` |
| AMQP event payload | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @ToString` (Jackson needs the no-args constructor) |
| Exception | `@Getter` + `@AllArgsConstructor`/`@RequiredArgsConstructor` over `private final` id fields |
| `@ConfigurationProperties` | `@Getter @Setter` (or `@Data`); nested `public static class` groups get `@Getter @Setter` too |
| Enum with fields | `@Getter @RequiredArgsConstructor` |

**Rules:**
- **Never put `@Data`, `@EqualsAndHashCode` or `@ToString` on entities.** They touch lazy associations and break hash-based collections.
- **`@Builder.Default` is required** on any entity or DTO field that has an initializer (`private boolean enabled = true;`). Without it, the builder silently drops the default.
- **Use a `record` instead of Lombok for:** Spring application events (`record FileSavedEvent(UUID fileId, ...)`), private error-data payloads in exception handlers, and small immutable internal values.
- **Use the builder** to construct entities and events: `RoomMember.builder().gameRoomId(id).isGuest(true).build()`.
- **`@Value` injection:** a bean that needs `@Value("${...}")` uses field injection for that value, and Lombok for the rest.

## 7. Entities

```java
@Entity
@Table(name = "room_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID gameRoomId;

    private String name;

    private boolean isGuest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> data;

    private Instant kickedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

- **Ids:** use `Long` IDENTITY for internal rows. Use a `UUID` id for things exposed in URLs or shared across services (rooms, files, puzzles).
- **Auditing:** every table has `created_at` and `updated_at`, filled by Spring Data auditing. `PersistenceConfig` carries `@EnableJpaAuditing`.
- **References:** reference other aggregates by **plain id fields** (`gameRoomId`, `companyId`). Add `@ManyToOne`/`@OneToMany` only when you really need to navigate the graph, and then make them `LAZY`.
- **Enums:** always `@Enumerated(EnumType.STRING)`.
- **JSON columns:** use hypersistence-utils `JsonType`, with `jsonb` on Postgres and `json` on MySQL.
- **Concurrency:** add `@Version private Long version;` (with `version BIGINT NOT NULL DEFAULT 0` in the migration) on rows that concurrent workers or pipeline stages update.
- **Behaviour on entities:** small, entity-local behaviour is fine as a method on the entity.
- **Schema validation:** `ddl-auto: validate`, so every field must match a migration column exactly.

## 8. Repositories

```java
@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    Optional<RoomMember> findByGameRoomIdAndId(UUID gameRoomId, Long id);

    List<RoomMember> findAllByGameRoomIdAndKickedAtIsNull(UUID gameRoomId);

    @Query("""
            SELECT r FROM RoomMember r
            WHERE r.gameRoomId IN :roomIds AND r.kickedAt IS NULL
            """)
    List<RoomMember> findAllNonKickedMembersByRoomIds(Set<UUID> roomIds);
}
```

- Prefer derived query names. Use `@Query` (JPQL in a text block) when the name would get unreadable.
- Single results return `Optional<T>`.
- **Tenant scoping:** queries scoped by tenant or owner include it in the method (`findByIdAndCompanyId`, `findByGameRoomIdAndId`).
- **Advisory locks (Postgres):** for create-if-absent races, use `pg_advisory_xact_lock(hashtext(:key))` in a native query, called inside `@Transactional`, with a one-line Javadoc saying why.

## 9. Services

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomMemberService {
    private static final Duration GUEST_JOIN_GRACE_PERIOD = Duration.ofSeconds(90);

    private final RoomMemberRepository roomMemberRepository;

    public RoomMember findMember(UUID roomId, Long memberId) {
        return roomMemberRepository.findByGameRoomIdAndId(roomId, memberId)
                .orElseThrow(() -> new RoomMemberNotFoundException(roomId.toString(), memberId));
    }

    @Transactional
    public void kickMember(UUID roomId, Long memberId) {
        var member = findMember(roomId, memberId);
        member.setKickedAt(Instant.now());
        roomMemberRepository.save(member);
        log.debug("Room {}. Member {}. Kicked", roomId, memberId);
    }
}
```

- **Method names:** `findX` returns `Optional` or throws NotFound, `getXOrThrow` throws, `findOrCreateX` upserts.
- **Transactions:** put `@Transactional` on public write methods that touch more than one row, or that must be atomic.
- **Normalising input:** normalise at the service boundary, e.g. `email.toLowerCase(Locale.ROOT)` and `name.trim()`.
- **Logging format:** prefix log lines with the context ids, e.g. `"Room {}. Member {}. ..."` or `"[game-order][fi-id: {}] ..."`.
  - Mask PII such as emails before logging them.
  - Use `debug` for flow, `info` for state changes, `warn` for handled problems, and `error` with the exception for failures.
- **Multi-stage pipelines:** keep a small `XxxMdc` helper holding the MDC key constants, `put(key, value)` and a `prefix()` method. Clear MDC per request with `MdcCleanupFilter` (`OncePerRequestFilter`) and per AMQP message with `MdcCleanupInterceptor`.

## 10. Controllers & DTOs

```java
@RestController
@RequestMapping("/v2/game/rooms/{roomId}/members")
@RequiredArgsConstructor
@Slf4j
public class RoomMemberController {
    private final RoomMemberService roomMemberService;

    @PostMapping("/{memberId}/name")
    public void setMemberName(
            @PathVariable UUID roomId,
            @PathVariable Long memberId,
            @RequestBody @Valid SetMemberNameRequest request
    ) {
        roomMemberService.updateMemberName(roomId, memberId, request.getName());
    }
}
```

- **Thin controllers:** validate, pull the principal or path ids, delegate to one service call, and return the DTO or `void`. No business logic.
- **Validation:** Jakarta annotations on request DTOs (`@NotNull(message = "...")`, `@NotBlank`, `@Email`). Use wrapper types (`Boolean`) when `@NotNull` matters.
- **Security:** pass the authenticated user with a custom `@CurrentUser UserPrincipal principal` argument. Express role checks as meta-annotations on the controller (`@IsCompanyAdmin`).
- **Mapping:** map between entities and DTOs by hand in a dedicated `XxxMapper` `@Component`, or in the service. No MapStruct.

## 11. Errors

Each failure case gets its own small unchecked exception that carries the ids needed for the message:

```java
@Getter
@AllArgsConstructor
public class RoomNotFoundException extends RuntimeException {
    private final String roomId;
}
```

**Shared model:**
- `model/ErrorResponse`: `timestamp`, `message`, `errorId`, `errorData`, `validationErrors`, with constructor overloads.
- `model/ErrorId`: an enum of stable machine codes the frontend can switch on (`ROOM_IS_FULL`, `PUZZLE_NOT_STARTED`).

**Handlers:** one or more `@RestControllerAdvice` classes in `exception/handlers`, grouped by area (`CommonExceptionHandlers`, `GameExceptionHandlers`, or a feature-local `DraftInvoiceExceptionHandler`):

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
@ExceptionHandler(RoomNotFoundException.class)
public ErrorResponse handle(RoomNotFoundException e, HttpServletRequest req) {
    var body = new ErrorResponse(Translator.toLocale("general.err.room_not_found", e.getRoomId()), ErrorId.ROOM_NOT_FOUND);
    exceptionLogger.logAndSentry(req, e, HttpStatus.NOT_FOUND, body);
    return body;
}
```

- Every handler method is named `handle`.
- When extra data must go to the client, declare a `private record XxxErrorData(...)` next to the handler and pass it as `errorData`.
- `ExceptionLogger` (`@Component`):
  - logs `HTTP {method} {uri} -> {status} {Exception}: {msg}` at warn, with the stack trace;
  - sends unexpected errors to Sentry (`logAndSentry`);
  - persists expected business errors (`logAndPersist`).
- **User-facing messages:** if the app is localised, resolve them via `Translator.toLocale(key, args...)` from `i18n/messages_{lang}.properties`.

## 12. Flyway migrations

**Location:** `src/main/resources/db/migration`.

**Versioned file name:** `V{yyyy.MM.dd}_{HH.mm}__{description}.sql`, using the current local date and time when the file is written. Get it with `date +%Y.%m.%d_%H.%M`. Examples:

```
V2025.07.31_15.30__create_room_members.sql
V2025.07.23_22.31__alter_game_rooms_add_invited_by_member_id.sql
V2025.08.07_23.07__rename_bonus_invites_to_teaser_invites.sql
V2025.11.11_05.38__create_indexes.sql
```

- **Description:** `snake_case`, starting with a verb: `create_{table}`, `alter_{table}_add_{column}`, `alter_{table}_drop_{column}`, `rename_{a}_to_{b}`, `create_indexes`.
- **Repeatable views:** `R__{Name}_view.sql`, starting with `DROP VIEW IF EXISTS x;` then `CREATE VIEW x AS ...`.
- **Never edit an applied migration.** Add a new one instead. `out-of-order: true` and `baseline-on-migrate: true` are on.
- **One logical change per file.** A data backfill (`UPDATE ...`) can share a file with the schema change it supports.

**PostgreSQL template:**

```sql
CREATE TABLE room_members
(
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    game_room_id UUID                        NOT NULL,
    name         VARCHAR(255),
    is_guest     BOOLEAN                     NOT NULL DEFAULT FALSE,
    data         JSONB,
    kicked_at    TIMESTAMP WITHOUT TIME ZONE NULL,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_members_game_room
        FOREIGN KEY (game_room_id) REFERENCES game_rooms (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_room_members_game_room_id ON room_members (game_room_id);
CREATE INDEX IF NOT EXISTS idx_room_members_room_not_kicked ON room_members (game_room_id) WHERE kicked_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_room_members_room_email ON room_members (game_room_id, email);
```

**MySQL template:**

```sql
CREATE TABLE shopify_fulfillments
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    version    BIGINT       NOT NULL DEFAULT 0,
    status     VARCHAR(50)  NOT NULL,
    payload    JSON,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shopify_fulfillments_status ON shopify_fulfillments (status);
```

**Constraint names:**
- `fk_{table}_{ref}` for foreign keys
- `idx_{table}_{cols}` for indexes
- `uq_{table}_{cols}` for unique constraints

**Indexing:** index every FK and every column you filter on. Use partial indexes (`WHERE ... IS NULL`, `WHERE is_active`) for hot "active rows" lookups, and `(x, created_at DESC)` for "latest per x".

**Stored values:** enums go in as `VARCHAR(255)` with the enum name. An inline `-- comment` on a column is fine when the format isn't obvious.

## 13. RabbitMQ (when the service uses messaging)

**`amqp/AMQPDefs` (`@Configuration`):**
- `EXCHANGE` (topic, durable) and `DLX_EXCHANGE = EXCHANGE + ".dlx"`;
- `QUEUE_*` constants;
- a `Declarables` bean;
- a `declareWithDlq(queueName, routingKey)` helper that creates the queue with `x-dead-letter-exchange`/`x-dead-letter-routing-key`, plus a `{queue}.dlq` queue, and both bindings.

**Events:** live in `{feature}/amqp/event`, use the Lombok set from section 6, and carry `public static final String ROUTING_KEY = "..."`. If the class is a wire contract with another repo, one short comment saying so is warranted.

**Producer:** `XxxEventSender` (`@Component`) wraps `rabbitTemplate.convertAndSend(AMQPDefs.EXCHANGE, Event.ROUTING_KEY, event)`, with one method per event.

**Consumer:** `XxxEventHandler` (`@Component`) with `@RabbitListener(queues = AMQPDefs.QUEUE_X, concurrency = "5-10")` methods named `onXxx(Event event)`. It delegates to a `XxxProcessor`/service.

**`config/amqp/RabbitConfig`:**
- `Jackson2JsonMessageConverter` using the app `ObjectMapper` (`@Primary`);
- a `SimpleRabbitListenerContainerFactory` with a stateless retry interceptor: 6 attempts, backoff 2s ×3 up to 3 min;
- `LoggingRejectAndDontRequeueRecoverer`, so exhausted messages go to the DLQ.

Also set `spring.rabbitmq.listener.simple.prefetch: 1`.

**Publishing after DB writes:** publish a Spring application event (a `record XxxSavedEvent(...)` in `jpa/event`), then send to Rabbit from a `@TransactionalEventListener(phase = AFTER_COMMIT)` method. That way consumers never race the commit. If that listener writes to the DB, add `@Transactional(propagation = REQUIRES_NEW)`.

## 14. Scheduled jobs (JobRunr)

Use JobRunr, not `@Scheduled`, for anything that must run once per cluster.

```java
public interface CronJob {
    CronIdentifier getIdentifier();
    default ZoneId getZoneId() { return ZoneId.of("Europe/Vilnius"); }
    @Job(name = "Executing %0", retries = 3)
    void execute(CronIdentifier id);
}

@Getter
@RequiredArgsConstructor
public enum CronIdentifier {
    DAILY_STAT("0 0 9 * * *"),
    ;
    private final String defaultCron;
}
```

- `cron_job/jobrunr/JobRunrScheduler` (`@Component`, `@PostConstruct`) injects `List<CronJob>` and calls `jobScheduler.scheduleRecurrently(id.name(), cron, zone, () -> handler.execute(id))` for each one.
- Each job is `cron_job/job/{Name}Job implements CronJob`, annotated `@Job(name = "...", retries = 3)`. It delegates to a service.
- JobRunr config: `use-virtual-threads: true`, `worker-count: 5`, dashboard on port 9091.
- Give JobRunr its own small datasource pool if the main pool is tight (`JobRunrDataSourceConfig`, `jobrunr.database.datasource`).

## 15. Configuration & profiles

### Profiles

Every project uses the same profiles. `application.yaml` never sets `spring.profiles.active`; whoever runs the app sets it.

| Profile | Used for | File | How it's activated |
|---|---|---|---|
| *(none)* | shared defaults | `application.yaml` | always loaded |
| `dev` | running locally against `docker-compose.yml` | `application-dev.yaml` | IDE run config or `./gradlew bootRun --args='--spring.profiles.active=dev'` |
| `remote` | **every deployed environment** (staging and prod) | `application-remote.yaml` | `SPRING_PROFILES_ACTIVE: remote` in `infrastructure/{server}/docker-compose.yml` |
| `test` | integration tests | `application-test.yaml` | `@ActiveProfiles("test")` on `BaseIntegrationTest` |

- **One `remote` profile for staging and prod.** Their differences come from env vars and the Parameter Store path, not from more profiles.
  - If staging and prod genuinely need different beans, stack a second profile (`remote,staging` / `remote,prod`).
  - Never reuse the name `dev` for a deployed server, because it collides with the local profile.
- **Observability is a file, not a profile.** `application-remote.yaml` pulls it in with `spring.config.import: "optional:classpath:application-observability.yml"`.
- **Switching beans by environment:** real adapters get `@Profile("remote")` and local or fake ones get `@Profile("!remote")` (e.g. S3 vs MinIO, real vs logging email sender). For a code-level check use `EnvironmentChecker.isDev()`.
- **Legacy naming:** an older repo may call the deployed profile `aws`. Keep that name inside that repo; new repos use `remote`.

### What goes in each file

`application.yaml` holds shared defaults and **no secrets**:

```yaml
server:
  port: 8080
  shutdown: graceful
spring:
  application:
    name: {app}
  threads:
    virtual:
      enabled: true
  flyway:
    enabled: true
    url: ${spring.datasource.url}
    user: ${spring.datasource.username}
    password: ${spring.datasource.password}
    baseline-on-migrate: true
    out-of-order: true
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  jackson:
    serialization:
      WRITE_DATES_AS_TIMESTAMPS: false
    time-zone: UTC
  data:
    web:
      pageable:
        default-page-size: 12
        max-page-size: 100
logging:
  level:
    root: INFO
    {root.package}: DEBUG
    org.hibernate: WARN
```

`application-dev.yaml` points at localhost on the ports from `docker-compose.yml`, with throwaway credentials such as `postgres`/`password` and `guest`/`guest`. **Never put real API keys here:** if a dev key is needed, read it from `${ENV_VAR:}`.

`application-remote.yaml` takes every host, secret and pool size from the environment:

```yaml
spring:
  config:
    import: "optional:classpath:application-observability.yml"
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_MAX}
      minimum-idle: ${DB_POOL_MIN}
      connection-timeout: 3000
      idle-timeout: 60000
      max-lifetime: 1200000
      leak-detection-threshold: 4000
  rabbitmq:
    addresses: ${RABBITMQ_ADDRESS}
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST}
app:
  cors.allowed.origins: ${APP_ORIGIN}
sentry:
  dsn: ${SENTRY_DSN}
```

**Where the values come from:** environment variables in the server's compose file, or AWS Parameter Store. With Parameter Store:
- add `spring-cloud-aws-starter-parameter-store` (through the `spring-cloud-aws-dependencies` BOM);
- set `SPRING_CONFIG_IMPORT: "aws-parameterstore:/config/{app}_{env}/"` on the container.

List every required variable name (no values) in `.env.example`.

**Optional startup check:** a `GlobalPropertyValidator` (`@Profile("remote")`) fails startup if a critical key is blank or still contains `${`.

### Config classes (`{root}/config`)

- `AppConfig` / `AppProperties`: `@ConfigurationProperties("app")`, with Lombok `@Getter @Setter` and nested static classes for sub-groups.
- `PersistenceConfig`: `@EnableJpaAuditing`.
- `TimeConfig`: a `Clock.systemUTC()` bean plus `TimeZone.setDefault(UTC)`.
- `WebMvcConfig`: CORS from `app.cors.allowed.origins`.
- `EnvironmentChecker`: `isDev()` based on the active profiles.
- `MdcCleanupFilter`: clears MDC after every request.
- `amqp/RabbitConfig`, `amqp/LoggingRejectAndDontRequeueRecoverer`, `amqp/MdcCleanupInterceptor`.
- `AsyncConfig` / `SchedulingConfig`: only when needed.

**Feature config (`{feature}/config`):** `XxxProperties` (`@ConfigurationProperties(prefix = "xxx")`, registered with `@EnableConfigurationProperties` on that feature's `XxxConfig`), plus HTTP client beans.

**HTTP clients:** use `RestClient`, built from the injected `RestClient.Builder` in a `@Bean` with `baseUrl` and default headers. Wrap each external API in an `XxxClient`/`XxxApiService` class.

## 16. Observability

Every deployed service ships metrics, traces, logs and errors. This is part of scaffolding, not an afterthought.

### App side

**Dependencies:**
- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`
- `micrometer-tracing-bridge-otel`
- `opentelemetry-exporter-otlp`
- `net.ttddyy.observation:datasource-micrometer-spring-boot` (SQL spans)
- `sentry-spring-boot-starter-jakarta` + `sentry-logback`

`src/main/resources/application-observability.yml`, imported by `remote`:

```yaml
management:
  server:
    port: 9092                    # actuator on its own port, never exposed via traefik
  endpoints:
    web:
      exposure:
        include: '*'
  tracing:
    enabled: true
    sampling:
      probability: 0.2
  otlp:
    tracing:
      endpoint: "http://grafana-alloy:4318/v1/traces"
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
      slo:
        http.server.requests: 200ms, 500ms, 1s, 3s
      maximum-expected-value:
        http.server.requests: 10s
jdbc:
  datasource-proxy:
    enabled: true
    include-parameter-values: true
    query:
      enable-logging: true
```

**Other app-side pieces:**
- **Sentry:** `sentry.dsn: ${SENTRY_DSN}` in remote. `ExceptionLogger.logAndSentry` captures with an `errorId` tag.
- **Container identity:** set `SPRING_APPLICATION_NAME: {app}_{env}` on the container, so metrics and traces are labelled per environment.
- **Logs:** write to stdout only. Alloy collects them from Docker, so there are no file appenders.

### Infra side

**Central stack, `observability/` in the main repo (one per region):**

```
observability/
  docker-compose.yml          # alloy, prometheus, loki, grafana, jaeger, postgres-exporter, cloudflared
  alloy-config.alloy          # alloy on the obs server itself
  prometheus.yml              # remote-write receiver enabled, 15d retention
  loki-config.yaml            # S3-backed storage
  grafana-provisioning/
    datasources/ds.yaml       # prometheus, loki, jaeger
    dashboards/               # dashboards-provider.yaml + micrometer, jvm, postgres, rabbit, docker, node-exporter JSON
  readme.md                   # one-off setup: postgres_exporter DB user, GitHub secrets
```

- **Network:** the stack uses an external `observability-network`. The deploy workflow creates it if it's missing.
- **Grafana:** published through a Cloudflare tunnel (`cloudflared`), not opened ports.
- **Images:** pin versions (`grafana/alloy:v1.x`, `prom/prometheus:v3.x`, `grafana/loki:3.x`, `grafana/grafana-oss:12.x`, `jaegertracing/jaeger:2.x`).

**Per app server, `infrastructure/{server}/alloy-config.alloy`:**
- `discovery.docker` finds the containers.
- Relabel rules keep only the named app containers, and scrape `{container}:9092/actuator/prometheus` as job `spring-boot`.
- `prometheus.exporter.unix` collects host metrics.
- `loki.source.docker` ships every container's logs, with an `env` label.
- `otelcol.receiver.otlp` (4317/4318) batches traces and forwards them to Jaeger.
- Everything is remote-written to the central Prometheus, Loki and Jaeger.

**Workflow, `.github/workflows/deploy-obs.yml`:**
- Triggers on pushes to `observability/**` on master.
- Patches `${DS_PROMETHEUS}` in community dashboards.
- `scp`s the folder to `/opt/observability`, then runs `docker compose up -d`.
- Secrets (exporter password, Loki S3 keys, tunnel token) come from GitHub secrets.

### Adding a new service to observability

1. Add the observability dependencies and `application-observability.yml` (above), and make sure remote imports it.
2. Give the container a stable `container_name` (`{app}-{env}`) and put it on the same network as that server's `grafana-alloy`.
3. Add the container name to the metrics relabel regex in that server's `alloy-config.alloy`.
4. Add `SENTRY_DSN` (and `SENTRY_AUTH_TOKEN` if you upload source context) to secrets.
5. If it has its own DB or RabbitMQ, add the matching exporter or dashboard.

## 17. Gradle

Use the Groovy DSL (`build.gradle`, `settings.gradle` with `rootProject.name = '{app-name}'`) and the Gradle wrapper 8.14+.

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.x'          // latest stable 3.x
    id 'io.spring.dependency-management' version '1.1.7'
}

group = '{reverse.domain}'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

compileJava.options.encoding = "UTF-8"
compileTestJava.options.encoding = "UTF-8"

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add("-parameters")
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation platform("io.awspring.cloud:spring-cloud-aws-dependencies:<latest>")

    // spring
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // db  (Postgres)
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'
    // db  (MySQL alternative)
    // implementation 'org.flywaydb:flyway-mysql'
    // runtimeOnly 'com.mysql:mysql-connector-j'

    // json columns
    implementation 'io.hypersistence:hypersistence-utils-hibernate-63:<latest>'

    // RabbitMQ (if needed)
    implementation 'org.springframework.boot:spring-boot-starter-amqp'

    // cron (if needed)
    implementation 'org.jobrunr:jobrunr-spring-boot-3-starter:<latest>'

    // aws
    implementation 'io.awspring.cloud:spring-cloud-aws-starter-parameter-store'

    // monitoring
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
    implementation 'net.ttddyy.observation:datasource-micrometer-spring-boot:<latest>'

    // sentry
    implementation 'io.sentry:sentry-spring-boot-starter-jakarta:<latest>'
    implementation 'io.sentry:sentry-logback:<latest>'

    // lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- Group dependencies under short `// spring`, `// db`, `// RabbitMQ`, `// monitoring` comments.
- Let the Spring BOM pick versions. Pin a version only when you must, and then say why on the same line.
- Look up current versions on Maven Central instead of guessing.
- **Integration tests:** add an `integrationTest` source set (`src/integrationTest/java`) that extends `testImplementation`/`testRuntimeOnly`/`testAnnotationProcessor`, plus an `integrationTest` Test task in group `verification`.

## 18. Tests

**Unit tests:**
- JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`) + AssertJ (`assertThat`, `assertThatThrownBy`).
- **One test class per behaviour of a service:** `RoomMemberServiceKickTest`, `PuzzleScoreServiceHintTest`.
- **Test method names:** descriptive camelCase (`kicksMemberBelongingToRoom`) or `shouldX_whenY`.
- Build fixtures with the entity builders. Put shared ids in `private static final` constants.
- Structure tests as arrange / act / assert separated by blank lines, without the comments.

**Integration tests:**
- Name them `*IT`. They extend an abstract `BaseIntegrationTest`: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, `@AutoConfigureMockMvc`.
- Testcontainers starts `src/integrationTest/resources/docker-compose-test.yml` once in a static block, and `@DynamicPropertySource` wires in the hosts and ports.
- A `TestDataFactory` `@Component` creates users and companies. Use `@Transactional` on the test classes so each test rolls back.

## 19. Docker, infrastructure & CI

`Dockerfile`:

```dockerfile
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

`docker-compose.yml` at the repo root is **local dev only**:
- `{app}-db`: `postgres:<version>-alpine`, exposed on an unusual host port (e.g. `11444:5432`), with data in `./.docker/postgres` and `docker_postgres_init_{app}.sql` mounted into `/docker-entrypoint-initdb.d/`.
- `{app}-rabbitmq`: `rabbitmq:4.1-management` on ports 5672 and 15672, if used.
- One named network: `{app}-network`.

`infrastructure/{server}/docker-compose.yml` describes what runs on each server. For each app container:
- `image`, `container_name: {app}-{env}`, `restart: unless-stopped`;
- `deploy.resources.limits.memory`, with a matching `JAVA_TOOL_OPTIONS: "-XX:+UseContainerSupport -Xmx… -Xms…"`. Leave about 250–500 MB above the heap for the JVM;
- traefik labels for host routing and TLS on port 8080. Never route the management port 9092;
- `environment`: `SPRING_APPLICATION_NAME: {app}_{env}`, `SPRING_PROFILES_ACTIVE: remote`, `AWS_REGION`, `SPRING_CONFIG_IMPORT: "aws-parameterstore:/config/{app}_{env}/"`;
- networks shared with traefik and `grafana-alloy`.

The server's traefik and alloy also live in this file.

**CI/CD (GitHub Actions):**
- **App workflows** (`deploy-staging.yml`, `deploy-master.yml`, triggered by branch or tag):
  - `actions/setup-java` (temurin 21), then `./gradlew bootJar`;
  - `docker build` and push to Docker Hub;
  - `appleboy/ssh-action` runs `docker compose pull <svc> && docker compose up -d --no-deps --force-recreate <svc>`;
  - the `.github/actions/slack-notify` composite action posts to Slack, with `if: always()`.
- **`deploy-infra-{server}.yml`:** copies `infrastructure/{server}/` to the server and applies it.
- **`deploy-obs.yml`:** section 16.

## 20. Scaffolding a new project

1. Ask: Postgres or MySQL? Confirm the app name, the reverse domain, and the optional modules (RabbitMQ, JobRunr, security/JWT).
2. Create the repo-root files from section 4:
   - `settings.gradle`, `build.gradle` and the Gradle wrapper
   - `Dockerfile`, local `docker-compose.yml`, `docker_postgres_init_{app}.sql`
   - `.env.example`, `.gitignore`, `CLAUDE.md` (sections 1–2 plus repo notes), `README.md`
3. Create `{App}Application`, `OkController` and the `config/` classes from section 15.
4. Set up error handling:
   - `model/ErrorResponse`, `model/ErrorId`
   - `exception/handlers/ExceptionLogger`
   - `exception/handlers/CommonExceptionHandlers`, including a `MethodArgumentNotValidException` handler that fills `validationErrors`
5. Create the config files: `application.yaml`, `application-dev.yaml`, `application-remote.yaml`, `application-test.yaml`, `application-observability.yml`.
6. Wire up observability: dependencies, and `application-observability.yml` imported by remote. Then add the service to the target server's `alloy-config.alloy` (section 16).
7. Add the optional modules: `amqp/AMQPDefs` + `config/amqp/*`, and the `cron_job/` skeleton.
8. Create `infrastructure/{server}/docker-compose.yml` entries and the deploy workflows (section 19).
9. Run `./gradlew build` and fix anything that fails.

## 21. Adding a feature to an existing project

1. Write the migration (section 12), timestamped now.
2. Write the entity (section 7) and make sure every column matches, because `ddl-auto: validate` will fail otherwise.
3. Add repository methods (section 8), scoped by tenant or owner.
4. Add the service(s) (section 9): small, single role, with `@Transactional` where needed.
5. Add the exception(s) and their handler methods (section 11), with an `ErrorId` if the frontend needs to react.
6. Add DTOs and a thin controller (section 10).
7. Wire any new properties:
   - a default in `application.yaml`;
   - a local value in `application-dev.yaml`;
   - `${ENV_VAR}` in `application-remote.yaml`;
   - the variable name in `.env.example`.
8. If the feature is async, add an event + `ROUTING_KEY`, the queue in `AMQPDefs` via `declareWithDlq`, a sender method and a handler method (section 13). Publish after commit.
9. Write unit tests for the service behaviour (section 18).
10. Run `./gradlew build`.
