# Messaging (RabbitMQ)

The `rabbit` scaffold module holds the full setup: `amqp/AMQPDefs.java` and `config/amqp/{RabbitConfig, LoggingRejectAndDontRequeueRecoverer, MdcCleanupInterceptor}.java`. Copy those; don't rewrite them.

## Topology

- One durable topic exchange per service (`AMQPDefs.EXCHANGE = "{service}"`) plus `{service}.dlx` for dead letters.
- Every queue the service **consumes** is added in `AMQPDefs.amqpDeclarables()` with `declareWithDlq(exchange, dlx, QUEUE_X, XxxEvent.ROUTING_KEY)`. That creates the queue with dead-letter arguments, a `{queue}.dlq`, and both bindings.
- A service declares only the queues it consumes. Producers publish to an exchange; consumers in other services declare their own queues on it.
- Names: `QUEUE_{FEATURE}_{EVENT} = "{service}.{feature}.{event}"` (e.g. `core.game_order.batch_requested`); routing keys are `snake_case` constants on the event class.

## Events (`{feature}/amqp/event/`)

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class GameOrderBatchCreatedEvent {
    public static final String ROUTING_KEY = "game_order_batch_created";

    private Long fulfillmentId;
    private List<LineItemResult> results;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class LineItemResult {
        private String lineItemId;
        private UUID roomId;
    }
}
```

- Past tense for facts (`XxxCreatedEvent`, `XxxRequestedEvent`, `XxxFetchedEvent`), imperative for commands (`FetchXxxEvent`, `ExecuteXxxEvent`).
- Carry ids and the minimum the consumer needs; prefer ids so the consumer reads fresh state.
- When the event is a contract with another repo, one line saying so is warranted: its field names can't change on one side only.

## Producer and consumer

```java
@Component
@RequiredArgsConstructor
public class GameOrderEventSender {
    private final RabbitTemplate rabbitTemplate;

    public void gameOrderBatchCreated(GameOrderBatchCreatedEvent event) {
        rabbitTemplate.convertAndSend(AMQPDefs.EXCHANGE, GameOrderBatchCreatedEvent.ROUTING_KEY, event);
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
public class GameOrderEventHandler {
    private final GameOrderBatchProcessor gameOrderBatchProcessor;

    @RabbitListener(queues = AMQPDefs.QUEUE_GAME_ORDER_BATCH_REQUESTED, concurrency = "5-10")
    public void onGameOrderBatchRequested(GameOrderBatchRequestedEvent event) {
        GameOrderMdc.put(GameOrderMdc.FULFILLMENT_ID, event.getFulfillmentId());
        log.debug("{} received batch with {} line items", GameOrderMdc.prefix(), event.getLineItems().size());
        gameOrderBatchProcessor.process(event);
    }
}
```

- One `XxxEventSender` per feature, one method per event, named after the event.
- One `XxxEventHandler` per feature with `onXxx` methods: set MDC, delegate to a `XxxProcessor`/service. No business logic in the handler.
- **Retry:** consumers throw to retry. `spring.rabbitmq.listener.simple.retry.*` in `application.yaml` retries 5 times (2s, 6s, 18s, 54s, 162s, capped at 3 min); then `LoggingRejectAndDontRequeueRecoverer` rejects without requeue and the broker dead-letters to `{queue}.dlq`.
- **Poison messages** that can never succeed (bad payload, a row that will never exist): throw `AmqpRejectAndDontRequeueException` to skip the retries.
- **Idempotency:** a message can arrive twice (redelivery after a crash, DLQ replay), so every stage checks state or writes under a unique key.
- `prefetch: 1` keeps dispatch fair across consumers.

## Publish only after the database commit

Publishing inside a transaction lets a consumer run before the row is visible. Publish a Spring application event in the transaction and send to Rabbit after commit:

```java
public record FileSavedEvent(UUID fileId, Long companyId) {
}

@Transactional
public File save(...) {
    var file = fileRepository.save(...);
    applicationEventPublisher.publishEvent(new FileSavedEvent(file.getId(), file.getCompanyId()));
    return file;
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onFileSaved(FileSavedEvent event) {
    fileEventSender.fileSaved(event.fileId());
}
```

- Application-event records live in `{feature}/jpa/event/`.
- If the after-commit listener itself writes to the database, add `@Transactional(propagation = Propagation.REQUIRES_NEW)`.

## Multi-stage pipelines and webhook intake

For work with several external steps (fetch order → ensure attributes → execute fulfilment), and for anything triggered by an inbound webhook:

1. **Intake:** the webhook controller verifies the signature (see [integrations.md](integrations.md)), saves or upserts a row keyed on the external id, publishes an application event, and returns 2xx immediately. The sender retries on non-2xx, so intake must be fast and idempotent.
2. **After commit,** the event listener publishes the first stage's queue event.
3. **One queue per stage, each with its own DLQ.** A failure retries only that stage; a parked message never re-runs earlier stages.
4. **State on the row:** each stage loads the row by id, does its step, saves progress or `lastError`, and publishes the next stage's event. `@Version` on the row makes concurrent stages fail loudly instead of overwriting each other.
5. **Correlation:** an `XxxMdc` helper holds the ids; every log line starts with `XxxMdc.prefix()` (see [services.md](services.md)).
6. Side effects that must not fail the webhook (an alert, a best-effort sync) run after the main work and never throw.
