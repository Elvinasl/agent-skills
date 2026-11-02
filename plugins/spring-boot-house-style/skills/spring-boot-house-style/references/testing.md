# Testing

## What must be tested

| Change | Required test |
|---|---|
| Service method with a branch, calculation or business rule | unit test per behaviour (happy path + each rule that can reject) |
| Bug fix | a test that fails without the fix |
| New endpoint | integration test: happy path, validation failure (400), and if secured: 401, 403, other tenant → 404 |
| Custom `@Query`, native SQL or `Specification` | integration test against the real database (Testcontainers) |
| New migration | covered by any integration test — the context starts only if Flyway applies and `ddl-auto: validate` passes |
| Queue consumer | unit test of the processor; integration test when retry/DLQ behaviour matters |
| Mapper with non-trivial logic | unit test |

Plain getters, Lombok-generated code and thin controllers don't need their own unit tests.

## Unit tests (`src/test/java`, run by `./gradlew test`)

```java
@ExtendWith(MockitoExtension.class)
class RoomMemberServiceKickTest {
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final Long MEMBER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private RoomMemberRepository roomMemberRepository;

    private RoomMemberService roomMemberService;

    @BeforeEach
    void setUp() {
        roomMemberService = new RoomMemberService(roomMemberRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void kicksMemberBelongingToRoom() {
        var member = RoomMember.builder().id(MEMBER_ID).roomId(ROOM_ID).build();
        when(roomMemberRepository.findByRoomIdAndId(ROOM_ID, MEMBER_ID)).thenReturn(Optional.of(member));

        roomMemberService.kickMember(ROOM_ID, MEMBER_ID);

        assertThat(member.getKickedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsMemberFromAnotherRoom() {
        when(roomMemberRepository.findByRoomIdAndId(ROOM_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomMemberService.kickMember(ROOM_ID, MEMBER_ID))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }
}
```

- JUnit 5 + Mockito + AssertJ. One test class per behaviour of a service: `RoomMemberServiceKickTest`, `PuzzleScoreServiceHintTest`.
- Use `@InjectMocks` when every dependency is a mock; construct the service yourself when it also needs a real value such as a fixed `Clock`.
- Method names describe the behaviour in camelCase (`kicksMemberBelongingToRoom`) or `shouldX_whenY`.
- Arrange / act / assert separated by blank lines — no `// given` comments.
- Build fixtures with entity builders; shared ids and times as `private static final` constants; time from a fixed `Clock`.
- Same package as the class under test.

## Integration tests (`src/integrationTest/java`, run by `./gradlew integrationTest`)

The template provides `TestcontainersConfig` (database and, with `rabbit`, a broker, wired via `@ServiceConnection`), `BaseIntegrationTest` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`) and `SmokeIT`.

```java
class RoomMemberControllerIT extends BaseIntegrationTest {

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void rejectsBlankName() throws Exception {
        var member = testDataFactory.member();

        mockMvc.perform(post("/rooms/{roomId}/members/{memberId}/name", member.getRoomId(), member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorId").value("VALIDATION_FAILED"));
    }
}
```

- Name classes `*IT`; extend `BaseIntegrationTest`.
- Create data through a `TestDataFactory` `@Component` (in `src/integrationTest/java`) using repositories and builders — not SQL scripts.
- Isolate tests: annotate the class `@Transactional` to roll back after each test when the code under test runs in the test's transaction; for code that commits on its own (async listeners, `REQUIRES_NEW`), clean up in `@AfterEach`.
- Replace a bean with `@MockitoBean` (Boot 4 removed `@MockBean`) — mainly for external HTTP clients.
- Slice tests are fine for focused cases: `@WebMvcTest` (`org.springframework.boot.webmvc.test.autoconfigure`) and `@DataJpaTest` (`org.springframework.boot.data.jpa.test.autoconfigure`).

## Before you call a change done

```bash
./gradlew build integrationTest
```

`build` runs unit tests; `integrationTest` needs Docker. If Docker isn't available, say so instead of skipping silently.
