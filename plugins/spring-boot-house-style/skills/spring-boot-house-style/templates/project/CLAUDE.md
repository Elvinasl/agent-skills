# __APP_NAME__

Java __JAVA_VERSION__ / Spring Boot 4.1 service. Follows the `spring-boot-house-style` skill.

## Run locally

```bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Verify

```bash
./gradlew build integrationTest
```

## Code style

- Keep comments to a minimum. Omit them whenever possible.
- Only document something when the WHY is genuinely non-obvious (a hidden constraint, a workaround, a subtle invariant) and the code cannot be made to express it through naming or structure alone — never to restate what the code already says.
- When documentation is genuinely needed, prefer Javadoc over inline comments.

## Git workflow

- Commit messages: one short line, starting with a lowercase letter, instructive/imperative mood. No `fix:`/`feat:`/other prefixes, no body, no Co-Authored-By trailer.
