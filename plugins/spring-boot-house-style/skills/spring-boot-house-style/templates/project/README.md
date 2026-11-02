# __APP_NAME__

## Requirements

- JDK __JAVA_VERSION__+
- Docker (local database and integration tests)

## Run locally

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The app listens on http://localhost:8080.

## Tests

```bash
./gradlew test               # unit tests
./gradlew integrationTest    # Testcontainers-backed tests, needs Docker
```

## Profiles

| Profile | Used for |
|---|---|
| `dev` | local run against `docker-compose.yml` |
| `remote` | every deployed environment; all secrets come from environment variables (see `.env.example`) |
| `test` | integration tests |
