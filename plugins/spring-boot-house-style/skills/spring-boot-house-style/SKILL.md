---
name: spring-boot-house-style
description: Use when creating or extending a Java/Spring Boot service — scaffolding a project, adding entities, Flyway migrations, endpoints, RabbitMQ consumers, JobRunr jobs or HTTP clients — so the code follows one consistent house style (Spring Boot 4.1, JDK 21+, Lombok, feature packages).
---

# Spring Boot house style

Code conventions for Spring Boot services. This skill covers application code only; deployment, infrastructure and observability are out of scope.

**Baseline for new projects:** Spring Boot 4.1, JDK 21 or newer, Gradle (Groovy DSL), Jackson 3, Hibernate 7, PostgreSQL or MySQL.

**In an existing repo, its conventions win.** Read its `CLAUDE.md`, then look at a neighbour of whatever you are adding (an entity, a service, the newest migration) and match it. If the repo is still on Boot 3, don't upgrade it unless asked; [references/boot4-upgrade.md](references/boot4-upgrade.md) lists what changes when you do.

## Workflows

### New project

1. Ask which database (**PostgreSQL** or **MySQL**) and which optional modules: `rabbit`, `jobrunr`, `sentry`. Confirm the app name (kebab-case), base package and JDK (default 21).
2. Generate it with the bundled script. It copies `templates/`, applies the choices, and fails if any placeholder is left:
   ```bash
   bash <skill-dir>/scripts/scaffold.sh --name order-sync --package com.acme.ordersync \
     --db postgres --with rabbit,jobrunr --java 21 --dir ./order-sync
   ```
   `--dir .` scaffolds into the current directory (it must be empty). `--db-port` changes the local database port.
3. `cd` in, run `./gradlew build` (and `./gradlew integrationTest` if Docker is available), and fix anything that fails before writing features.

Do not hand-write the skeleton — the templates are the source of truth for build files, config classes, error handling and test setup. If you cannot run Bash, reproduce what the script does by hand: copy `templates/project/` plus `templates/modules/<module>/` for each chosen module, rename `gitignore` → `.gitignore` and `env.example` → `.env.example`, move `__PACKAGE_DIR__` to the package path, replace the `__APP_NAME__`, `__APP_CLASS__`, `__PACKAGE__`, `__GROUP__`, `__DB_NAME__`, `__DB_PORT__` and `__JAVA_VERSION__` tokens, keep the lines between `>>>mod:x` / `<<<mod:x` markers for chosen modules (dropping the marker lines), and delete those blocks for the others.

### New feature in an existing service

1. **Migration** — `V{yyyy.MM.dd}_{HH.mm}__{verb}_{table}.sql`, timestamped now. → [persistence](references/persistence.md)
2. **Entity + repository** — every column matches the migration (`ddl-auto: validate`). → [persistence](references/persistence.md)
3. **Service(s)** — small, single-role, `@Transactional` where atomic. → [services](references/services.md)
4. **Exceptions + handlers** — one exception per failure case, add an `ErrorId` if a client must react. → [web-and-errors](references/web-and-errors.md)
5. **DTOs + thin controller.** → [web-and-errors](references/web-and-errors.md)
6. **Properties** — default in `application.yaml`, local value in `application-dev.yaml`, `${ENV_VAR}` in `application-remote.yaml`, name in `.env.example`. → [configuration](references/configuration.md)
7. **Async work** — event + queue with DLQ, published after commit. → [messaging](references/messaging.md)
8. **Tests** — per the rules in [testing](references/testing.md).
9. Run `./gradlew build`.

## Reference files — read the one you need

| Working on | Read |
|---|---|
| Entities, repositories, JSON columns, paging/filtering, locking, Flyway | [references/persistence.md](references/persistence.md) |
| Services, transactions, time, logging/MDC, alerts | [references/services.md](references/services.md) |
| Controllers, DTOs, validation, error responses, i18n | [references/web-and-errors.md](references/web-and-errors.md) |
| RabbitMQ, events, after-commit publishing, webhook intake pipelines | [references/messaging.md](references/messaging.md) |
| Outbound HTTP clients, inbound webhook signatures, service-to-service auth | [references/integrations.md](references/integrations.md) |
| Spring Security, JWT/API-key filters, `@CurrentUser`, role annotations | [references/security.md](references/security.md) |
| Recurring jobs | [references/jobs.md](references/jobs.md) |
| Profiles, YAML files, `@ConfigurationProperties`, secrets | [references/configuration.md](references/configuration.md) |
| What to test and how | [references/testing.md](references/testing.md) |
| Moving a Boot 3 repo to 4.1 | [references/boot4-upgrade.md](references/boot4-upgrade.md) |

## Rules that always apply

### Package layout — by feature, not by layer

Root package `{reverse-domain}.{appname}` (dashes removed). Feature packages are `snake_case`.

```
{root}/
  {App}Application.java   OkController.java
  amqp/AMQPDefs.java                  # exchanges, queues, DLQs
  config/                             # app-wide config; config/amqp/ for Rabbit
  exception/  exception/handlers/     # shared exceptions, @RestControllerAdvice, ExceptionLogger
  model/                              # ErrorResponse, ErrorId, shared enums
  i18n/  cron_job/  util/
  {feature}/
    controller/  controller/dto/      # XxxController, XxxRequest, XxxResponse
    jpa/  jpa/event/                  # entities, repositories, Spring app events (records)
    service/                          # business logic
    model/                            # feature value types, enums, XxxView
    amqp/  amqp/event/                # XxxEventSender, XxxEventHandler, XxxEvent
    exception/  config/               # feature exceptions; XxxProperties, client beans
```

### Naming

| Thing | Convention | Example |
|---|---|---|
| Entity | domain noun, **no `Entity` suffix** | `RoomMember` |
| Repository | `{Entity}Repository` | `RoomMemberRepository` |
| Table / column | plural `snake_case` / `snake_case`, booleans `is_x`, times `x_at` | `room_members.kicked_at` |
| DTOs | `XxxRequest`, `XxxResponse`; UI read models `XxxView` | `SetMemberNameRequest` |
| Exceptions | per case: `XxxNotFoundException`, `XxxAlreadyExistsException`, `XxxIsFullException` | `RoomNotFoundException` |
| Services | named for one role; `XxxService` only for a broad façade | `PuzzleRestarter`, `InvoiceMapper`, `OrderProcessor` |
| Events | facts in past tense, commands as imperatives | `OrderCreatedEvent`, `FetchOrderEvent` |
| REST paths | plural kebab-case, nested by ownership | `/rooms/{roomId}/members/{memberId}/name` |
| Config keys | kebab-case under a feature prefix | `app.cors.allowed-origins` |
| Queues / routing keys | `{service}.{feature}.{event}` / `snake_case` | `core.game_order.batch_requested` |
| Migrations | `V{yyyy.MM.dd}_{HH.mm}__{verb}_{table}.sql` | `V2026.11.01_21.40__create_room_members.sql` |

### Lombok

| Class | Annotations |
|---|---|
| Spring bean | `@RequiredArgsConstructor` + `private final` fields, `@Slf4j` if it logs. Never `@Autowired`. |
| Entity | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` — never `@Data`, `@EqualsAndHashCode` or `@ToString` |
| Request DTO | `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` (+ `@Builder` if built in code) |
| Response DTO / view | `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, or a `record` |
| AMQP event | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor @ToString` |
| Exception | `@Getter` over `private final` id fields + a constructor |
| `@ConfigurationProperties` | `@Getter @Setter`, nested `static` classes for groups |
| Enum with fields | `@Getter @RequiredArgsConstructor` |

Any field with an initializer in a `@Builder` class needs `@Builder.Default`. Use records for Spring application events, handler error-data payloads and small immutable values.

### Code style

- Minimal comments. Comment only a non-obvious *why* (hidden constraint, workaround, wire contract), and prefer Javadoc to inline `//`.
- `var` for locals when the type is obvious; Java 21 features freely (records, text blocks for JPQL/SQL, pattern matching, `.toList()`).
- `Optional` + `orElseThrow(() -> new XxxNotFoundException(...))` instead of null checks.
- Time is UTC everywhere; get "now" from the injected `Clock` (`Instant.now(clock)`), never `Instant.now()` in business code.
- Map entities ↔ DTOs by hand (an `XxxMapper` component or the service). No MapStruct.
- Never commit secrets: dev YAML holds throwaway local credentials only.

### Git

One short lowercase imperative line: `add vat amount edit request`. No `feat:`/`fix:` prefixes, no body, no Co-Authored-By trailer. Commit or push only when asked.
