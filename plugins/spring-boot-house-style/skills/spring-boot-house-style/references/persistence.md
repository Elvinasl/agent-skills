# Persistence: entities, repositories, Flyway

## Entity

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
    private UUID roomId;

    private String name;

    private boolean isGuest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> data;

    private Instant kickedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

- **Ids:** `Long` IDENTITY for internal rows; `UUID` for ids that appear in URLs or cross service boundaries.
- **Auditing:** every table has `created_at` and `updated_at`, filled by Spring Data auditing. `PersistenceConfig` enables it with the `Clock`-backed `DateTimeProvider` from `TimeConfig`, so tests with a fixed clock get deterministic timestamps.
- **References:** point at other aggregates with plain id fields (`roomId`, `companyId`). Use `@ManyToOne(fetch = FetchType.LAZY)` / `@OneToMany` only when you really navigate the graph inside one transaction.
- **Enums:** always `@Enumerated(EnumType.STRING)`, stored as `VARCHAR`.
- **JSON columns:** `@JdbcTypeCode(SqlTypes.JSON)` on a `Map`, record or POJO. Hibernate 7 serialises it with Jackson 3 automatically, so no extra library is needed. The column is `JSONB` (Postgres) or `JSON` (MySQL).
- **Optimistic locking:** `@Version private Long version;` on rows that concurrent workers or pipeline stages update.
- **Behaviour:** small, entity-local behaviour may live on the entity (`member.kick(now)`); anything that needs a repository or another aggregate belongs in a service.
- `ddl-auto: validate` is on, so every mapped field must match a migrated column exactly.

## Repository

```java
@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long>, JpaSpecificationExecutor<RoomMember> {

    Optional<RoomMember> findByRoomIdAndId(UUID roomId, Long id);

    List<RoomMember> findAllByRoomIdAndKickedAtIsNull(UUID roomId);

    @Query("""
            SELECT m FROM RoomMember m
            WHERE m.roomId IN :roomIds AND m.kickedAt IS NULL
            """)
    List<RoomMember> findActiveByRoomIds(Set<UUID> roomIds);
}
```

- Derived query names first; `@Query` with a JPQL text block when the name would be unreadable; native SQL only for database features JPQL lacks.
- Single results return `Optional<T>`.
- **Tenant/owner scoping is part of the method name** (`findByIdAndCompanyId`, `findByRoomIdAndId`), so one tenant can never load another tenant's row by id.
- **Projections:** list screens return an interface or record projection instead of full entities.

## Paging and filtering

- List endpoints take a `Pageable` and return `Page<XxxView>`. The template sets `spring.data.web.pageable.serialization-mode: via-dto`, so the JSON shape is a stable `PagedModel`. Defaults: page size 20, max 100.
- Optional filters go into a `Specification`, built by static factory methods in `{feature}/jpa/specification/XxxSpecifications`. Always start from the tenant restriction and add filters to it:

```java
public final class InvoiceSpecifications {

    private InvoiceSpecifications() {
    }

    public static Specification<Invoice> forCompany(Long companyId, InvoiceFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("companyId"), companyId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
```

- When a permission filter yields "no allowed ids", return `cb.disjunction()` (matches nothing) — never drop the restriction.

## Concurrency

- **Lost updates** between workers → `@Version`.
- **Create-if-absent races** (two requests both create "the" row) → a unique index plus catching `DataIntegrityViolationException`, or on Postgres a transaction-scoped advisory lock taken first inside the `@Transactional` method:

```java
/**
 * Serialises member creation per room; seats are picked by reading existing members, so two concurrent joins would
 * otherwise take the same seat. Must be called inside a transaction.
 */
@Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtext(:roomId)) AS text)", nativeQuery = true)
String lockMemberCreation(String roomId);
```

- **Idempotent handlers** (webhooks, queue consumers) key their writes on the external id with a unique index, so a redelivery updates instead of duplicating.

## Flyway migrations

**Location:** `src/main/resources/db/migration`.

**Versioned:** `V{yyyy.MM.dd}_{HH.mm}__{description}.sql`, timestamped with the current local time when written (`date +%Y.%m.%d_%H.%M`). The description is `snake_case` and starts with a verb:

```
V2026.11.01_21.40__create_room_members.sql
V2026.11.02_09.15__alter_room_members_add_kicked_at.sql
V2026.11.03_10.00__rename_bonus_invites_to_teaser_invites.sql
V2026.11.04_08.30__create_indexes_for_member_lookup.sql
```

**Repeatable views:** `R__{Name}_view.sql`, beginning `DROP VIEW IF EXISTS x;` then `CREATE VIEW x AS ...`.

**Rules:**
- Never edit a migration that has run anywhere; add a new one. `baseline-on-migrate` and `out-of-order` are on, so a migration merged from a parallel branch with an older timestamp still applies.
- One logical change per file; a data backfill may share the file with the schema change it supports.
- Every table has `id`, `created_at`, `updated_at`. Store `Instant` as `TIMESTAMPTZ` on Postgres and `TIMESTAMP(6)` on MySQL (the JVM and connection run in UTC).
- Constraint names: `fk_{table}_{ref}`, `uq_{table}_{cols}`, `idx_{table}_{cols}`.
- Index every foreign key and every column you filter or sort on. Use partial indexes for hot "active rows" lookups (`WHERE kicked_at IS NULL`) and `(x, created_at DESC)` for "latest per x".
- An inline `-- comment` on a column is fine when its format is not obvious (`-- gid://shopify/Order/123`).

### PostgreSQL

```sql
CREATE TABLE room_members
(
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    room_id    UUID         NOT NULL,
    name       VARCHAR(255),
    email      VARCHAR(255),
    is_guest   BOOLEAN      NOT NULL DEFAULT FALSE,
    role       VARCHAR(50)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    data       JSONB,
    version    BIGINT       NOT NULL DEFAULT 0,
    kicked_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_room_members_room_id ON room_members (room_id);
CREATE INDEX IF NOT EXISTS idx_room_members_room_active ON room_members (room_id) WHERE kicked_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_room_members_room_email ON room_members (room_id, email);
```

### MySQL

```sql
CREATE TABLE room_members
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id    BINARY(16)   NOT NULL,
    name       VARCHAR(255),
    email      VARCHAR(255),
    is_guest   BOOLEAN      NOT NULL DEFAULT FALSE,
    role       VARCHAR(50)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    data       JSON,
    version    BIGINT       NOT NULL DEFAULT 0,
    kicked_at  TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE,
    CONSTRAINT uq_room_members_room_email UNIQUE (room_id, email)
);

CREATE INDEX idx_room_members_room_id ON room_members (room_id);
```

MySQL has no partial indexes; use a composite index such as `(room_id, kicked_at)` instead.
