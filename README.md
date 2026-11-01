# agent-skills

Reusable skills for Claude (Claude Code, the Claude app) and other coding agents.
Each skill is a plain `SKILL.md` file that teaches the agent a specific way of working, so it writes code the way you would every time.

## Skills

| Skill | What it does |
|---|---|
| [`spring-boot-house-style`](plugins/spring-boot-house-style/skills/spring-boot-house-style/SKILL.md) | Opinionated Java 21 / Spring Boot 3 conventions for scaffolding new services and extending existing ones. |

### spring-boot-house-style

Distilled from three production Spring Boot services. When you ask the agent to create a project, add an entity, write a migration, or wire up a queue, it follows these rules:

- **Layout:** feature-first packages (`room_member/controller`, `/jpa`, `/service`, `/model`, `/amqp/event`) and a fixed map of where every config file lives.
- **Naming:**
  - entities without an `Entity` suffix;
  - `XxxRequest` / `XxxResponse` / `XxxView` DTOs;
  - single-role services (`PuzzleRestarter`, `XxxMapper`, `XxxProcessor`);
  - plural kebab-case REST paths.
- **Lombok:** which annotations go on beans, entities, DTOs, events, exceptions and config classes, plus the pitfalls (`@Builder.Default`, no `@Data` on entities).
- **JPA:**
  - audited `created_at` / `updated_at`;
  - plain-id references between aggregates;
  - `@Version` for concurrent writers;
  - tenant-scoped repository methods.
- **Flyway:** `V{yyyy.MM.dd}_{HH.mm}__create_table.sql` naming, `R__` views, constraint and index naming, and templates for both PostgreSQL and MySQL.
- **Errors:** one exception per failure case, `ErrorResponse` + `ErrorId`, and `@RestControllerAdvice` handlers logged to Sentry.
- **Profiles:**
  - `dev` (local), `remote` (every deployed environment) and `test`;
  - secrets only from env vars or AWS Parameter Store;
  - `@Profile("remote")` vs `@Profile("!remote")` adapters.
- **Messaging:** RabbitMQ topic exchange with a DLQ per queue, retry with backoff, and publishing only after the DB transaction commits.
- **Scheduling:** JobRunr recurring jobs through a `CronJob` interface and a `CronIdentifier` enum.
- **Observability:**
  - actuator on a separate port, Prometheus metrics, OTLP traces and SQL spans;
  - Grafana Alloy → Prometheus / Loki / Jaeger, provisioned Grafana dashboards, Sentry.
- **Build & deploy:**
  - Gradle (Groovy DSL, Java 21 toolchain), an integration-test source set with Testcontainers;
  - Docker, per-server compose files behind Traefik, GitHub Actions deploys.
- **Code style:** minimal comments (Javadoc only for a non-obvious *why*), and one-line lowercase imperative commit messages.

Inside an existing repo, the skill tells the agent to match that repo's existing conventions first.

## Install

### Claude Code: plugin marketplace (recommended)

Inside a Claude Code session:

```
/plugin marketplace add Elvinasl/agent-skills
/plugin install spring-boot-house-style@elvinasl-skills
```

Or from your shell:

```bash
claude plugin marketplace add Elvinasl/agent-skills
claude plugin install spring-boot-house-style@elvinasl-skills
```

Claude loads the skill automatically whenever you work on a Java/Spring Boot project. You can also call it explicitly with `/spring-boot-house-style:spring-boot-house-style`.

To get updates later, run `/plugin`, open the marketplace and update it.

### Claude Code: copy the skill folder

For all your projects:

```bash
git clone https://github.com/Elvinasl/agent-skills.git
mkdir -p ~/.claude/skills
cp -r agent-skills/plugins/spring-boot-house-style/skills/spring-boot-house-style ~/.claude/skills/
```

For a single repository, copy the folder into that repo's `.claude/skills/` instead, and commit it so your team gets it too.

### Claude app (claude.ai / desktop)

1. Zip the folder `plugins/spring-boot-house-style/skills/spring-boot-house-style` (the zip must contain `SKILL.md`).
2. Upload it as a custom skill in the app's skill settings.

### Other agents

`SKILL.md` is plain Markdown with a short YAML header. You can point any agent that reads instruction files (for example via `AGENTS.md` or a rules file) at it, or paste its contents into your project instructions.

## Example prompts

- "Create a new Spring Boot service called `order-sync` with Postgres and RabbitMQ."
- "Add a `teaser_invites` table, entity, repository and an endpoint to create one."
- "Publish an event when an invoice is parsed and consume it in the export feature."
- "Add a nightly job that cleans up expired rooms."
- "Wire this service into observability."

## Repository layout

```
.claude-plugin/
  marketplace.json                         # marketplace catalog (lists every plugin)
plugins/
  spring-boot-house-style/
    .claude-plugin/plugin.json             # plugin manifest
    skills/
      spring-boot-house-style/
        SKILL.md                           # the skill itself
```

### Adding another skill

1. Create `plugins/<plugin-name>/.claude-plugin/plugin.json` and `plugins/<plugin-name>/skills/<skill-name>/SKILL.md`.
2. Add an entry to `.claude-plugin/marketplace.json`, with the same `name` as in `plugin.json`.
3. Validate with `claude plugin validate .` and test locally with `claude plugin marketplace add ./`.

## Contributing

Issues and pull requests are welcome. If a rule doesn't fit your project, open an issue describing the case. The skill is opinionated on purpose, but rules that are unclear or wrong should be fixed.

## License

[MIT](LICENSE)
