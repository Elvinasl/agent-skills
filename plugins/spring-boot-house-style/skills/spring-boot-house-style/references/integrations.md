# Integrations: outbound HTTP, inbound webhooks, service-to-service calls

## Outbound HTTP clients

Each external API is wrapped in one class in its own feature package (`shopify/`, `okredo/`), so the rest of the code never sees URLs, headers or the provider's payload shapes.

**Preferred: declarative HTTP service interfaces** (Spring Framework 7). Configuration comes from properties per group:

```java
@HttpExchange
public interface ExchangeRatesClient {

    @GetExchange("/rates/{currency}")
    RatesResponse rates(@PathVariable String currency);
}

@Configuration
@ImportHttpServices(group = "exchange-rates", types = ExchangeRatesClient.class)
public class ExchangeRatesConfig {
}
```

```yaml
spring:
    http:
        serviceclient:
            exchange-rates:
                base-url: https://api.example.com
                connect-timeout: 2s
                read-timeout: 10s
```

**When you need full control** (GraphQL, signing, custom error mapping), build a `RestClient` bean from the injected `RestClient.Builder` in `{feature}/config/XxxConfig` and wrap it in an `XxxClient` / `XxxApiService`:

```java
@Bean
public RestClient shopifyRestClient(RestClient.Builder builder, ShopifyProperties properties) {
    return builder
            .baseUrl(properties.getStore().getUrl())
            .defaultHeader("X-Shopify-Access-Token", properties.getApp().getAdminAccessToken())
            .build();
}
```

Rules:
- **Always set timeouts** — globally with `spring.http.clients.connect-timeout` / `read-timeout`, or per service group. A missing read timeout eventually hangs a thread.
- **Map provider errors to your own exceptions** in the client class (`ShopifyApiException`), with the operation and the provider's message. Callers never catch `RestClientResponseException`.
- **Treat "200 with errors" as a failure.** GraphQL APIs and some REST APIs return HTTP 200 with an `errors[]` / `userErrors[]` body — check it on every call, or a rejected write looks like a success.
- Check for a response with no data and fail with a clear message, instead of an NPE several frames later.
- Read responses into typed records; use `JsonNode` only for one-off shapes used in a single place.
- Log the operation and the provider's correlation id, never tokens or full request bodies.

## Inbound webhooks: verify the signature first

Verify HMAC signatures in a `OncePerRequestFilter` scoped to the webhook paths. The body must be read once, hashed, then replayed to the controller:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyHmacFilter extends OncePerRequestFilter {

    private final ShopifyProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var wrapped = new CachedBodyRequestWrapper(request);
        if (isValid(wrapped.getBody(), request.getHeader("X-Shopify-Hmac-Sha256"))) {
            chain.doFilter(wrapped, response);
        } else {
            log.warn("Rejected webhook with invalid signature. Path: {}", request.getServletPath());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private boolean isValid(byte[] body, String signature) {
        var secret = properties.getApp().getClientSecret();
        if (signature == null || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = Base64.getEncoder().encode(mac.doFinal(body));
            return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/shopify/webhooks/");
    }
}
```

- `CachedBodyRequestWrapper` extends `HttpServletRequestWrapper`, reads `getInputStream().readAllBytes()` once, and returns a fresh `ServletInputStream` over those bytes from `getInputStream()`.
- Compare signatures with `MessageDigest.isEqual` (constant time), not `String.equals`.
- Unconfigured secret means reject, never accept.
- Webhook controllers return 2xx fast and hand off to a pipeline; see [messaging.md](messaging.md).
- Gate webhook controllers that must be off in some environments with `@ConditionalOnProperty(prefix = "xxx.webhook", name = "enabled", havingValue = "true")`.

## Service-to-service calls

- Internal endpoints live under `/internal/**` in a separate `InternalXxxController`.
- The caller sends a shared secret header (e.g. `X-Internal-Secret`), read from `${INTERNAL_SECRET}`; the receiver checks it in a filter or a small validator and compares in constant time.
- If Spring Security is on, give `/internal/**` its own rule so it doesn't fall through to user authentication (see [security.md](security.md)).
- Prefer asynchronous events over synchronous internal calls when the caller doesn't need the answer immediately.
