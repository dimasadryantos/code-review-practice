# Answer key — Exercise 5: PortfolioService.kt (Kotlin)

Scalable's backend is Kotlin-first, so the snippet may be Kotlin. Most issues are language-agnostic
(you can say them in Java terms); the Kotlin-specific ones are marked **[KT]**.

## 🔴 Blocking

1. **Unbounded, unsynchronised cache that is never invalidated on price change.**
   `mutableMapOf` (a `LinkedHashMap`) shared by request threads → data race; grows per customer
   forever; a valuation computed at 09:00 is served all day, even after the market moves, and
   `rebalance` uses this stale total to compute targets → **wrong trades**. Fix: `ConcurrentHashMap`
   or Caffeine with `expireAfterWrite`, keyed by customer + price snapshot time; or don't cache
   valuations at all — cache prices.

2. **`rebalance` is marked `@Transactional` but calls `valuation()` via `this` and runs remote
   price calls inside the transaction.** Self-invocation means the caching/side-effects in
   `valuation` aren't transactional anyway, and holding a DB transaction open across N network calls
   (`runBlocking { priceClient… }`) → connection-pool exhaustion under load. Also **[KT]**: Spring
   proxies need the class/method to be `open`; with the `kotlin-spring` plugin `@Service`/
   `@Transactional` classes are opened automatically — **ask whether the plugin is configured**;
   without it `@Transactional` silently doesn't apply (Kotlin classes are `final` by default).

3. **Rebalance only sells, never buys, and mutates positions directly.** `if (currentValue >
   targetValue)` reduces units; positions below target are never increased; the proceeds vanish —
   money disappears from the books. It also *writes new unit counts* instead of **creating orders**;
   a rebalance should produce sell/buy orders that go through the order pipeline, with the position
   updated on execution. Domain-level blocker.

4. **`Double` for money, units, weights, plus `targetWeights.values.sum() != 1.0`** — floating-point
   equality: `0.1 + 0.2 + 0.7 != 1.0`. Legit input gets rejected, or sloppy input gets accepted.
   → `BigDecimal` and compare with a tolerance / exact scale.

5. **`!!` on remote data** — `price!!` throws `NullPointerException` the moment the price service
   has no quote for an ISIN; `p.currency!!` in `format`. **[KT]** `!!` in production code is a
   review flag by itself; handle the null (`?: throw PriceUnavailable(isin)`) or make the type
   non-null at the boundary. Note `priceOf` does the opposite: `?: 0.0` silently values a position
   at zero → rebalance sells everything else to compensate. Two different null strategies for the
   same call = inconsistency and a bug.

## 🟠 Should fix

6. **[KT] `runBlocking` in a request-serving Spring MVC service** — blocks the servlet thread on
   each call, N times in a loop (sequential price fetches; 50 positions × 100 ms = 5 s). Either make
   the service `suspend` end-to-end (WebFlux/coroutines) or use a blocking client; if you must bridge,
   fetch all prices concurrently with `coroutineScope { positions.map { async { … } }.awaitAll() }`,
   or batch endpoint `latestPrices(isins)`.

7. **[KT] `GlobalScope.launch`** — unstructured concurrency: no cancellation, no error propagation
   (exception kills nothing, logs nothing), survives the request, leaks on shutdown. Audit log entry
   may never be written → **auditability** requirement in finance. → inject a `CoroutineScope`
   tied to the application lifecycle with a `SupervisorJob` + exception handler, or write the audit
   record synchronously in the same transaction / via outbox. For an audit log, fire-and-forget
   is the wrong reliability level.

8. **[KT] `lateinit var baseCurrency`** on a singleton — mutable public state, throws
   `UninitializedPropertyAccessException` if read before set, nothing sets it in this file. →
   constructor parameter from `@ConfigurationProperties`, `val`.

9. **[KT] `data class` with `var` properties used as a JPA/domain entity** — `equals`/`hashCode`
   include all fields, so mutating `units` changes the hash while it's in a `HashSet`/map; `copy()`
   bypasses invariants. Prefer `val` and `copy`, or a normal class for entities. `Valuation` holding
   mutable `Position`s means the cached object is mutated by `rebalance` → cache corruption.

10. **Shared `SimpleDateFormat`** again (not thread-safe) and `asOf` as a `String` → `Instant` /
    `OffsetDateTime` with `DateTimeFormatter`.

11. **`priceOf` called twice per position inside the loop** (and prices may differ between calls) →
    fetch once into a `Map<isin, price>`.

12. **`println` instead of a logger**; `.also { println }` in a pipeline.

13. **`positionsAbove` sorts by `units`** though the filter is about *value* — probably wrong sort
    key; also returns entities directly.

14. **No validation** that `targetWeights` keys are the customer's ISINs, no negative weights check.

## 🟢 Nits

- `format` builds a user-facing string in a service → presentation layer.
- Interfaces defined at the bottom of the service file.
- `customerId: String` everywhere → value class `@JvmInline value class CustomerId(val v: String)`.
- `var currency: String? = null` default — currency should never be unknown for a position.

## The 60-second version

> "The core problem is that `rebalance` trusts a cached valuation that's never invalidated and is
> computed with `Double`, then directly rewrites unit counts instead of creating orders — and it
> only sells, so value disappears from the books. I'd make that a 'generate orders' operation with
> BigDecimal and fresh prices. Second, the Kotlin concurrency: `runBlocking` inside a request loop
> and `GlobalScope.launch` for the audit log mean slow requests and audit entries that can silently
> vanish — I'd fetch prices concurrently with structured concurrency and write audit records
> reliably, in-transaction or via an outbox. Third, the nullability is inconsistent: `!!` in one
> place, `?: 0.0` in another, for the same price call — both are wrong, missing prices must fail
> loudly. Smaller: the shared `SimpleDateFormat`, `lateinit var` config, mutable data classes in a
> cache, and I'd check the `kotlin-spring` plugin is on, otherwise `@Transactional` isn't even
> applied."

## Kotlin review cheat sheet (things interviewers expect you to spot)

| Smell | Why | Prefer |
|---|---|---|
| `!!` | NPE on bad data | `?:` with a meaningful exception, non-null boundary types |
| `lateinit var` on singletons | hidden mutable state, init-order bugs | constructor `val` |
| `GlobalScope.launch` | unstructured, leaks, swallows errors | injected scope / `coroutineScope` |
| `runBlocking` in prod code | blocks threads, deadlock risk | `suspend` all the way or blocking client |
| `data class` with `var` | mutable equals/hashCode | `val` + `copy()` |
| `== 1.0` on `Double` | float equality | `BigDecimal` / tolerance |
| `Any`/`Map<String, Any>` payloads | no types | sealed classes / data classes |
| `catch (e: Exception) {}` | swallowed | specific types, `runCatching` carefully |
| `!!`-free but `?: default` for money | silently wrong numbers | fail fast |
| Final classes + Spring AOP | proxies can't subclass | `kotlin-spring` plugin / `open` |
| `companion object` mutable state | shared static | avoid |
| `java.util.Date` / `SimpleDateFormat` | mutable, not thread-safe | `java.time` |
