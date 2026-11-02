---
type: llm
---
Look only at the Java files the agent wrote.
PASS if all of these hold:
- the controller is thin: it validates the request with @Valid and makes a single service call, with no repository access;
- the request DTO uses Jakarta validation for "required" and "max 50 characters";
- there is a dedicated RoomMemberNotFoundException carrying the room id and member id;
- a @RestControllerAdvice method named `handle` maps it to 404 and returns the house ErrorResponse with an ErrorId;
- there is no catch-all @ExceptionHandler(Exception.class);
- no class uses @Autowired; dependencies come through constructor injection (Lombok @RequiredArgsConstructor).
FAIL if any of them is missing.
