---
description: Adding a table to a Postgres-backed Spring Boot 4 service produces a correctly named migration, entity and repository in feature packages.
tags: [persistence]
max_turns: 30
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit]
---

I have an existing Spring Boot 4.1 service with root package `com.acme.game` on PostgreSQL. Add a new table for teaser invites: each invite belongs to a room (room id is a UUID), has the invitee email, an optional accepted-at time, and the id of the member who sent it. Write the Flyway migration, the JPA entity and the repository as files in this directory under the usual src/main paths. Don't run Gradle.
