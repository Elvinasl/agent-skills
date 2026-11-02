---
description: Scaffolding a new service uses Boot 4.1 starters, JDK 21+, the dev/remote profiles and drops unused modules.
tags: [scaffold]
max_turns: 80
timeout_seconds: 900
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

Scaffold a new Spring Boot service in this directory. Name: `order-sync`, root package `com.acme.ordersync`, PostgreSQL, JDK 21. It needs scheduled jobs but no RabbitMQ and no user accounts. Put the project directly in the current directory. Just create the project files; don't run Gradle or ask me anything else.
