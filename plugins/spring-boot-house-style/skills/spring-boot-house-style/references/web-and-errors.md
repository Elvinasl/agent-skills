# Controllers, DTOs, errors, i18n

## Controller

```java
@RestController
@RequestMapping("/rooms/{roomId}/members")
@RequiredArgsConstructor
public class RoomMemberController {
    private final RoomMemberService roomMemberService;

    @GetMapping
    public Page<RoomMemberView> list(@PathVariable UUID roomId, Pageable pageable) {
        return roomMemberService.listMembers(roomId, pageable);
    }

    @PostMapping("/{memberId}/name")
    public void setName(
            @PathVariable UUID roomId,
            @PathVariable Long memberId,
            @RequestBody @Valid SetMemberNameRequest request
    ) {
        roomMemberService.updateName(roomId, memberId, request.getName());
    }
}
```

- Thin: validate, take path ids / principal, make **one** service call, return a DTO or `void`. No business logic, no repositories.
- Paths are plural kebab-case and nested by ownership. Use `ResponseEntity` only when the status or headers vary.
- Pass the authenticated user as `@CurrentUser UserPrincipal principal` (see [security.md](security.md)).

## DTOs

- `controller/dto/XxxRequest` with Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` and Jakarta validation (`@NotNull`, `@NotBlank`, `@Email`, `@Size`, `@Positive`). Use wrapper types (`Boolean`, `Long`) when `@NotNull` must catch a missing field.
- `controller/dto/XxxResponse` or `model/XxxView` — a `record`, or `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.
- Never return entities from controllers.

## Errors

Each failure case is its own unchecked exception carrying the ids the message needs:

```java
@Getter
public class RoomMemberNotFoundException extends RuntimeException {
    private final UUID roomId;
    private final Long memberId;

    public RoomMemberNotFoundException(UUID roomId, Long memberId) {
        super("member " + memberId + " not found in room " + roomId);
        this.roomId = roomId;
        this.memberId = memberId;
    }
}
```

Shared model (in the template):

- `model/ErrorResponse` — record `(timestamp, message, errorId, errorData, validationErrors)`, null fields omitted.
- `model/ErrorId` — enum of stable machine codes clients can switch on. Add one when a client must react differently (`ROOM_IS_FULL`, `PUZZLE_NOT_STARTED`).

Handlers live in `@RestControllerAdvice` classes in `exception/handlers`, grouped by area (`CommonExceptionHandlers`, `GameExceptionHandlers`) or next to a feature (`{feature}/exception/XxxExceptionHandler`). Every method is named `handle`:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
@ExceptionHandler(RoomMemberNotFoundException.class)
public ErrorResponse handle(RoomMemberNotFoundException e, HttpServletRequest req) {
    exceptionLogger.expected(req, e, HttpStatus.NOT_FOUND);
    return ErrorResponse.of(translator.translate("member.err.not_found", e.getMemberId()), ErrorId.MEMBER_NOT_FOUND);
}
```

- When the client needs structured data, declare a `private record XxxErrorData(...)` next to the handler and pass it as `errorData`.
- `ExceptionLogger.expected(...)` — business outcomes (not found, conflict, full): one `warn` line, no stack trace.
- `ExceptionLogger.unexpected(...)` — "should never happen" states you still map to a response (e.g. two hosts in one room): `error` with stack trace, and to Sentry when that module is on.
- Do **not** add a catch-all `@ExceptionHandler(Exception.class)`: it would turn Spring Security's 401/403 and Spring MVC's 405/415 into 500s. Unhandled exceptions go to Boot's default error handling (and Sentry).
- The template already handles `MethodArgumentNotValidException` (field violations in `validationErrors`), unreadable bodies and `BadRequestException`.
- Status choice: 400 invalid input, 401 unauthenticated, 403 not allowed, 404 missing (also for "exists but belongs to another tenant"), 409 state conflict, 423 locked, 429 capacity/rate.

## i18n

- Messages in `src/main/resources/i18n/messages.properties` (+ `messages_{lang}.properties`). Keys are `{area}.err.{case}` / `{area}.msg.{case}`.
- `Translator.translate(code, args...)` resolves against the request locale.
- Supported languages are `app.supported-locales`; `LocalizationConfig` resolves `Accept-Language` against that list and falls back to English. Adding a language = new properties file + one entry in `app.supported-locales`.
