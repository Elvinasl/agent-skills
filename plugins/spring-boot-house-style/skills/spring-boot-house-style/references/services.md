# Services, time, logging

## Shape

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomMemberService {
    private static final Duration GUEST_JOIN_GRACE_PERIOD = Duration.ofSeconds(90);

    private final RoomMemberRepository roomMemberRepository;
    private final Clock clock;

    public RoomMember findMember(UUID roomId, Long memberId) {
        return roomMemberRepository.findByRoomIdAndId(roomId, memberId)
                .orElseThrow(() -> new RoomMemberNotFoundException(roomId, memberId));
    }

    @Transactional
    public void kickMember(UUID roomId, Long memberId) {
        var member = findMember(roomId, memberId);
        member.setKickedAt(Instant.now(clock));
        log.debug("Room {}. Member {}. Kicked", roomId, memberId);
    }
}
```

- **One role per class.** When a service grows past a couple of responsibilities, split out a class named for the job: `PuzzleRestarter`, `PuzzleFinder`, `PuzzleResultCalculator`, `InvoiceMapper`, `OrderProcessor`, `PaymentNotifier`, `DuplicateChecker`, `XxxResolver`, `XxxValidator`. Keep `XxxService` for a feature's broad façade.
- **Method naming:** `findX` returns `Optional` *or* throws NotFound when the caller always needs it; `getXOrThrow` when both exist; `findOrCreateX` for upserts.
- **Transactions:** `@Transactional` on public methods that write more than one row or must be atomic. Inside a transaction, a managed entity is saved on commit — calling `save()` again is optional. Never call a `@Transactional` method on `this` expecting a new transaction; self-invocation bypasses the proxy.
- **Constants** at the top as `private static final`.
- **Normalise input at the boundary:** `email.trim().toLowerCase(Locale.ROOT)`, `name.trim()`.
- **Time:** inject `Clock` and use `Instant.now(clock)`; tests pass `Clock.fixed(...)`.

## Logging

- `@Slf4j`; `debug` for flow, `info` for state changes, `warn` for handled problems, `error` with the exception for failures.
- Prefix lines with the ids that identify the work: `"Room {}. Member {}. Kicked"` or `"[order][ord-id: {}] fetched"`.
- Mask personal data before logging it (`ab*****` for emails); never log tokens, passwords or full payment details.
- **Multi-stage pipelines** keep a small `XxxMdc` helper: key constants, `put(key, value)` that ignores nulls, and `prefix()` that renders every key currently set in a fixed order. Call sites log `log.info("{} fetched order", OrderMdc.prefix())` instead of threading ids through every message. MDC is cleared by `MdcCleanupFilter` (HTTP) and `MdcCleanupInterceptor` (AMQP) — never by call sites.

## Alerts

An operational alert sender (Slack webhook, email) is a `@Component` named `XxxNotifier` that:

- reads its target URL from config and treats "not configured" as a supported state (log the alert at `warn` and return);
- **never throws** — a failed alert is logged with its full text, because reporting a problem must not become a second failure (or a dead-lettered message);
- takes a title plus ordered `label → value` pairs and renders plain text.
