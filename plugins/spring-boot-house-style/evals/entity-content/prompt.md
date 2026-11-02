---
description: The entity follows the Lombok, auditing and enum rules.
tags: [persistence, lombok]
max_turns: 25
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit]
---

Spring Boot 4.1 project, package `com.acme.billing`. Write a JPA entity for a subscription: it has a customer id (Long), a status (ACTIVE, PAUSED, CANCELLED), the next billing time, a JSON blob with the raw provider payload, and it is updated concurrently by queue consumers. Save the entity to `Subscription.java` in the current directory (not under src/) and also write the matching Postgres migration to `migration.sql` in the current directory. Don't run Gradle.
