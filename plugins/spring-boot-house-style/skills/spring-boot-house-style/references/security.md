# Security (Spring Security 7)

Add `spring-boot-starter-security` (and `spring-boot-starter-security-test` for tests) only when the service authenticates users or API keys. Webhook-only services use signature filters instead ([integrations.md](integrations.md)).

## Filter chain

`security/config/SecurityConfig`:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(it -> it.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(it -> it.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/error", "/public/**", "/auth/login", "/auth/refresh-token").permitAll()
                        .requestMatchers("/internal/**").hasRole("INTERNAL")
                        .anyRequest().authenticated())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

- Stateless JWT APIs disable CSRF and sessions. Never `permitAll()` broad prefixes like `/api/**`.
- `.cors(Customizer.withDefaults())` uses the CORS mappings from `WebMvcConfig`.
- The entry point writes the house `ErrorResponse` JSON with 401, so clients get one error shape everywhere.

## Authentication filters

- `JwtAuthenticationFilter extends OncePerRequestFilter`: read `Authorization: Bearer …`, decode and verify, convert claims to a `UserPrincipal`, set `SecurityContextHolder.getContext().setAuthentication(...)`, then **always** call `chain.doFilter`. An invalid or expired token simply leaves the request unauthenticated; the chain's rules return 401. Log decode failures at `debug`, not `error`.
- Use asymmetric keys (RS256/ES256) loaded from config, and keep access tokens short-lived with a refresh-token table.
- `ApiKeyAuthenticationFilter`: look up hashed keys (never store raw keys), attach the owning company as the principal.

## Using the principal

```java
@Target({ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {
}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public @interface IsCompanyAdmin {
}
```

```java
@RestController
@RequestMapping("/document-type-configs")
@IsCompanyAdmin
@RequiredArgsConstructor
public class DocumentTypeConfigController {

    @GetMapping
    public List<DocumentTypeConfigResponse> list(@CurrentUser UserPrincipal principal) {
        return documentTypeConfigService.list(principal.getCompanyId());
    }
}
```

- Role and permission checks are meta-annotations named for the rule (`@IsCompanyAdmin`, `@RequirePartyAccess`), applied to the controller class or method — never inline `hasRole(...)` strings scattered across controllers.
- Tenant ids (`companyId`) come **only** from the principal, never from the request body or path, and are passed to tenant-scoped repository methods.
- A row that exists but belongs to another tenant is reported as 404, not 403.

## Tests

- Controller tests: `@WithMockUser` or `.with(user(principal))` from `spring-security-test`.
- Integration tests cover at least: anonymous → 401, wrong role → 403, other tenant's id → 404.
