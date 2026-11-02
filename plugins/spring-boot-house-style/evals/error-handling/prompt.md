---
description: A new endpoint gets a validated request DTO, a per-case exception and a handler in the house error shape.
tags: [web, errors]
max_turns: 30
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit]
---

Existing Spring Boot 4.1 service, root package `com.acme.game`, which already has `model/ErrorResponse`, `model/ErrorId`, `exception/handlers/ExceptionLogger` and `i18n/Translator` from the house template. A `RoomMember` entity and `RoomMemberRepository` (with `findByRoomIdAndId(UUID, Long)`) exist in `com.acme.game.room_member.jpa`. Add `POST /rooms/{roomId}/members/{memberId}/name` that renames a member; the name is required and at most 50 characters, and an unknown member must return 404 with a machine-readable error id. Write the files under `src/main/java` in this directory. Don't run Gradle.
