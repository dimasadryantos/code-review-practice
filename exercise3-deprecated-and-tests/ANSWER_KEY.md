# Answer key — Exercise 3: OrderService ("deprecated libraries and broken tests")

A candidate described Scalable's review snippet as "easy screening (using deprecated libraries and
broken tests)". This exercise is built to that description. Score: you should catch every item in
the 🔴 list and recognise at least 5 deprecated/legacy APIs by name.

## 🔴 Blocking

1. **`@Transactional` on a `private` method called from the same class — it does nothing.**
   Spring wraps the bean in a proxy; the proxy only intercepts *external* calls to *public* methods.
   `placeOrder → this.saveAndUpdatePosition()` bypasses the proxy, so the order save and position
   update are **not atomic**: order persisted, position update fails → customer paid, no units.
   Fix: make `placeOrder` itself `@Transactional` (public, called from controller), or move the
   method to a separate bean. **This is the "how do Spring annotations work internally" question
   in disguise — expect them to ask it.** Same problem with `@Async sendConfirmation` called via
   `this` — runs synchronously, the `Thread.sleep(200)` blocks the request thread.

2. **`Float` for money and units.** `100.0f / 3` loses cents; `amountEur == 0.0f` on a boxed `Float`
   compares by `==` → autounboxing works here, but `Float` vs `Float` with `==` elsewhere compares
   references. Fix: `BigDecimal` with scale, or `long` minor units; units with defined precision
   (fractional ETF units often 6 dp).

3. **Position update is a lost-update race.** Read units → add → save, no lock, no `@Version`. Two
   orders for the same customer/ISIN at once (savings plan + manual buy) → one is lost.
   Fix: optimistic locking (`@Version`) with retry, or `UPDATE positions SET units = units + ?` in
   the DB, or pessimistic lock. Also: position should be **derived from filled orders**, not
   incremented on order *placement* — the order isn't executed yet (`status = NEW`)! Domain bug.

4. **Shared static `SimpleDateFormat`** — not thread-safe; concurrent calls produce garbage dates or
   `NumberFormatException`. Fix: `DateTimeFormatter` (immutable) + `LocalDate`/`Instant`.

5. **Division by `price` without null/zero check.** `getForObject` can return `null` → NPE on
   unboxing; price 0 → `Infinity` units. Also the price is fetched **inside** order placement from
   a remote service with no timeout (default `RestTemplate` = infinite) → request thread hangs.

## 🟠 Should fix

6. **Deprecated / legacy APIs** (name as many as you can — this is what they're testing):
   - `java.util.Observable` / `Observer` — deprecated since Java 9; also a `@Service` extending
     `Observable` mixes framework concerns into domain, and `notifyObservers` runs listeners
     synchronously on the request thread, before the transaction (if there were one) commits.
     → Spring `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`.
   - `new URL(String)` — deprecated in Java 20; use `URI.create(...)`. Here it's also pointless
     (`new URL(...).toString()`).
   - `new Integer(int)` — deprecated since Java 9 (boxing constructor); and storing `createdAt` as
     epoch-seconds `Integer` → **Y2038** overflow. → `Instant`.
   - `new Date()` + `SimpleDateFormat` → `java.time`.
   - `java.util.logging.Logger` with string concatenation → SLF4J with placeholders.
   - `RestTemplate` — in maintenance mode; Spring 6 recommends `RestClient`/`WebClient`. Also
     `new RestTemplate()` inside the bean: no timeouts, no injection, untestable.
   - `@MockBean` — deprecated in Spring Boot 3.4 in favour of `@MockitoBean`.
   - JUnit 4 `@Ignore` in a JUnit 5 test — **does nothing**, the test runs (and fails or passes
     wrongly). Mixed JUnit 4/5 on the classpath is a smell in itself.
   - `Thread.sleep` in tests → Awaitility / mocking the async executor.

7. **Field injection (`@Autowired` on fields)** → constructor injection: immutability, explicit
   dependencies, testable without Spring.

8. **Error handling**: `throws Exception` from a public API; `new Exception("...")` checked generic;
   `catch (InterruptedException e) { // ignore }` swallows the interrupt → should re-set
   `Thread.currentThread().interrupt()`. Validation only rejects `== 0`, not negative or `null` amount,
   blank ISIN, unknown customer.

9. **`o.getStatus() == "NEW"`** string identity comparison → `.equals`, or an enum.

10. **`cancelAll` N+1 saves in a loop** with no transaction → partial cancellation on failure. Bulk
    `UPDATE … WHERE customer_id = ? AND status = 'NEW'`.

11. **Hard-coded `http://` internal URL** as a mutable field — config property, HTTPS, no trailing
    slash games.

12. **Magic strings** `"NEW"`, `"CANCELLED"`, date stored as a formatted `String`.

## 🔴 The tests (a separate blocking item — say this explicitly: "the test suite gives false confidence")

- **Test order dependency**: `test2` and `test4` rely on `static lastOrder` set by `test1`. JUnit 5
  doesn't guarantee order by default → `NullPointerException` or green by accident. Static mutable
  state in tests is a red flag.
- **`test2` asserts against a hard-coded price of 10** that is never stubbed — the real
  `RestTemplate` is called → test hits the network (or fails with connection refused). The service's
  `RestTemplate` isn't injectable, so the price can't be mocked at all → design problem exposed by
  the test.
- **`test3` has no assertion**; `Thread.sleep(500)` "waits" for an async method that isn't actually
  async (see #1). Passing means nothing.
- **`test4` is `@Disabled` with no reason** — the one test for `cancelAll` is switched off. Ask why.
- **`test5` uses JUnit 4 `@Ignore`** → it actually runs, and since the method throws `Exception`
  the test **fails**. "Flaky on CI" was a misdiagnosis: it's not flaky, it's a missing
  `assertThrows`. Correct form: `assertThrows(IllegalArgumentException.class, () -> …)`.
- **`test6` asserts `5f`** — the *old* value — after placing a 100 EUR order. Either the test is
  wrong or it's documenting the bug that positions get updated before execution. Either way: a
  test that passes while asserting nothing changed is broken.
- **`@SpringBootTest` for a unit test** — boots the whole context (slow), mocks the repos but talks
  to real HTTP. Should be a plain Mockito unit test with constructor injection; keep one slice/
  integration test with Testcontainers for the transaction behaviour.
- Nothing tests: negative amount, null price, concurrent orders, transaction rollback.

## The 60-second version

> "Two things I'd block on. First, the `@Transactional` and `@Async` are on methods called via
> `this`, so Spring's proxy never sees them — the order and position writes aren't atomic and the
> 'async' confirmation blocks the request. Second, money and units are `Float`, and the position
> is incremented on placement, not on execution, with a read-modify-write race on top. Then there's
> a cluster of deprecated APIs — `Observable`, `new URL(String)`, `new Integer`, `SimpleDateFormat`
> shared across threads, `RestTemplate` without timeouts, `@MockBean` — which I'd clean up in the
> same PR. And the tests give false confidence: they depend on execution order through a static,
> one has no assertion, one uses JUnit 4 `@Ignore` so it isn't actually ignored, and one asserts
> the position didn't change after a buy. I'd ask the author to rewrite them as plain Mockito unit
> tests with constructor injection, plus one Testcontainers test for the transaction."

## Follow-ups to expect

- "Why doesn't `@Transactional` work on a private method?" → proxy-based AOP, CGLIB subclass, self-
  invocation. Fix options: self-inject, separate bean, `AspectJ` weaving, `TransactionTemplate`.
- "What's the difference between `@Async` with and without a configured executor?" →
  `SimpleAsyncTaskExecutor` creates unbounded threads by default.
- "When would you use `@TransactionalEventListener`?" → side effects only after commit.
- "How would you test the concurrency bug?" → Testcontainers Postgres, two threads, `CountDownLatch`,
  assert final units.
