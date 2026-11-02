# Upgrading a Boot 3 repo to Boot 4.1

Read this when a repo is still on Boot 3.x. Don't upgrade unasked, and never mix styles: in a Boot 3 repo, write Boot 3 code. Boot 3.5 open-source support ended on 2026-06-30, so propose the upgrade when you touch an old repo.

| Area | Boot 3.x | Boot 4.1 |
|---|---|---|
| Gradle | 7.x/8.x | 8.14+ or 9.x |
| Web | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| Flyway | `flyway-core` + `flyway-database-*` | add `spring-boot-starter-flyway` (keep `flyway-database-postgresql` / `flyway-mysql`) |
| RestClient | in the web starter | `spring-boot-starter-restclient`; declarative clients via `@ImportHttpServices` + `spring.http.serviceclient.<group>.*` |
| AOP | `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| Test starters | `spring-boot-starter-test` | per technology: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test` (each brings `spring-boot-starter-test`) |
| `@AutoConfigureMockMvc` / `@WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| Mocks | `@MockBean`, `@SpyBean` | `@MockitoBean`, `@MockitoSpyBean` |
| Jackson | 2.x, `com.fasterxml.jackson.databind.*`, `ObjectMapper` | 3.x, `tools.jackson.databind.*`, `JsonMapper` bean (annotations stay in `com.fasterxml.jackson.annotation`); `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`; `@JsonComponent` → `@JacksonComponent` |
| JSON columns | `hypersistence-utils-hibernate-6x` `@Type(JsonType.class)` | `@JdbcTypeCode(SqlTypes.JSON)` — Hibernate 7.4 uses Jackson 3 automatically |
| AMQP converter | `Jackson2JsonMessageConverter` | `JacksonJsonMessageConverter` (Jackson 2 one is deprecated for removal) |
| AMQP retry | custom `RetryInterceptorBuilder.stateless().maxAttempts(n)` | `spring.rabbitmq.listener.simple.retry.{enabled,max-retries,initial-interval,multiplier,max-interval}` + a `MessageRecoverer` bean; `maxAttempts` became `maxRetries` (retries after the first attempt) |
| Testcontainers | 1.x: `org.testcontainers:postgresql`, `org.testcontainers.containers.PostgreSQLContainer<?>` | 2.x: `org.testcontainers:testcontainers-postgresql`, `org.testcontainers.postgresql.PostgreSQLContainer` (same for `-mysql`, `-rabbitmq`, `-junit-jupiter`); prefer `@ServiceConnection` over `@DynamicPropertySource` |
| JobRunr | `jobrunr-spring-boot-3-starter` 8.x; `use-virtual-threads`, `skip-create-database-tables` | `jobrunr-spring-boot-4-starter` 9.x; `background-job-server.thread-type: VirtualThreads`, `database.skip-create` |
| Sentry | `sentry-spring-boot-starter-jakarta` | `sentry-spring-boot-4-starter` |
| Undertow | supported | removed — use Tomcat (default) or Jetty |

## Order that works

1. Move to the latest 3.5.x and clear every deprecation warning.
2. Bump Gradle (wrapper) and the Boot plugin to 4.1.x; switch starters (web → webmvc, add flyway/restclient/test starters).
3. Move Jackson imports to `tools.jackson`, replace `ObjectMapper` injection with `JsonMapper`.
4. Replace hypersistence `JsonType` with `@JdbcTypeCode(SqlTypes.JSON)`.
5. Switch the AMQP converter and move retry to properties + `MessageRecoverer` bean.
6. Update JobRunr/Sentry artifacts and properties.
7. `@MockBean` → `@MockitoBean`; Testcontainers 2 artifacts and packages; fix test annotation imports.
8. `./gradlew build integrationTest`.

The official guide is the source of truth when something here disagrees: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
