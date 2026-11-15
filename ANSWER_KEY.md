# Answer key — PR #482 (don't open until you've written MY_REVIEW.md)

Scoring yourself: a strong senior review names **all 🔴 items**, most 🟠, and *prioritises* them
out loud. Nits are a bonus; listing only nits is a fail.

---

## 🔴 Blocking — would cause money loss, data corruption, or a security incident

### 1. No transaction around the balance updates — `TransferService.transfer`
Two separate `save()` calls with no `@Transactional`. If the second save fails (DB down, constraint
violation, the pod gets killed), money has left `from` and never arrived at `to`. In a bank this is
the single worst bug in the PR.
**Fix:** `@Transactional` on the method so debit, credit and the transfer row commit or roll back together.
Follow-up question an interviewer may ask: *"Does `@Transactional` work if you call this method from
another method in the same class?"* — No, Spring's proxy only intercepts external calls.

### 2. Race condition / lost update on balance — `transfer`
Read balance → check → write, with no locking. Two concurrent transfers from the same account both
read 100, both pass the `< amount` check, both subtract → account goes negative (double spend).
Across two accounts, concurrent transfers in opposite directions can also deadlock once you add locks
in the wrong order.
**Fix:** either pessimistic lock (`@Lock(PESSIMISTIC_WRITE)` on `findById` / `SELECT … FOR UPDATE`,
lock accounts in a **consistent order**, e.g. by id, to avoid deadlock) or optimistic lock
(`@Version` column + retry on `OptimisticLockException`). Mention you'd pick optimistic for low
contention, pessimistic when contention is high. Also mention that the DB should enforce
`CHECK (balance >= 0)` as a last line of defence.

### 3. `double` for money — `Account.balance`, `Transfer.amount`, `MAX_TRANSFER`, controller parsing
`0.1 + 0.2 != 0.3`. Rounding errors accumulate; `Double.parseDouble` on client input makes it worse.
**Fix:** `BigDecimal` with a defined scale and `RoundingMode`, or store minor units as `long` (cents).
Column should be `DECIMAL(19,4)`, not `DOUBLE`.

### 4. Idempotency is broken — `processedRequests` is an in-memory `HashMap`
- Not thread-safe (`HashMap` under concurrent writes can corrupt itself).
- Lost on restart and not shared across pods → a client retry after a deploy produces a **duplicate
  transfer**.
- Grows forever → memory leak.
- Check-then-act isn't atomic even with `ConcurrentHashMap`: two identical retries arriving at the
  same time both miss the map and both execute.
**Fix:** persist the idempotency key in the DB with a **unique constraint** (`requestId` column on
`transfers`, or a dedicated table) and let the DB reject the duplicate; catch the constraint violation
and return the existing transfer. Add a TTL/cleanup if using a separate store (Redis `SET NX EX`).

### 5. Hard-coded production API key in source — `NotificationClient.API_KEY`
Secret in git history forever. Immediate rotation needed.
**Fix:** inject via config/secrets manager (`@Value("${notifications.api-key}")`, Vault, AWS Secrets
Manager, env var). Add a secret-scanning pre-commit hook.

### 6. No authorization check — `transfer` / `history` / `totalSent`
Nothing verifies that the caller owns `fromAccountId`. Any authenticated user can move money out of
any account by guessing/knowing an id, and read anyone's history. The PR says "between their own
accounts" but never enforces it.
**Fix:** take the principal from the security context and check `from.getOwnerId()` matches; filter
history/totalSent by owner too. Never trust ids from the request body for ownership.

### 7. Frozen accounts are ignored — `Account.frozen`
The entity has a `frozen` flag but `transfer` never checks it (neither side). Compliance/AML freeze
would be bypassed.
**Fix:** reject if either account is frozen; cover it with a test.

### 8. No currency check
`from.currency` vs `to.currency` never compared. Transferring 100 EUR into a USD account credits 100
USD. The PR note "we can add currency conversion later" is not a reason to silently corrupt balances
now.
**Fix:** reject mismatched currencies (`400`) until conversion exists.

### 9. Tests don't test anything — `TransferServiceTest`
- `transferWorks`: `assertTrue(true)`; never asserts balances (70 / 30), status, or that `save` was
  called with the right values.
- `insufficientFunds`: swallows the exception in a try/catch, so the test **passes even if no
  exception is thrown**. Should be `assertThrows(InsufficientFundsException.class, …)`.
- No tests for: same account, negative/zero amount, missing account, frozen, currency mismatch,
  idempotent retry, concurrency.
- Mocking JPA repos to test a transaction-critical flow means the real risk (#1, #2) is never
  exercised — suggest a `@DataJpaTest`/Testcontainers test that runs two transfers in parallel.
**Fix:** rewrite with real assertions; add the missing cases. "Green locally" is meaningless here.

---

## 🟠 Should fix before merge — correctness, robustness, operability

### 10. Missing input validation
No check for: `amount <= 0` (negative amount = **reverse transfer**, steals money), `from == to`,
null/blank ids, null `requestId` (then `processedRequests.put(null, …)` silently works). Also
`body.get("amount").toString()` NPEs on a missing field.
**Fix:** a proper `TransferRequest` DTO with `@Valid`, `@NotBlank`, `@DecimalMin("0.01")`, and an
explicit `from != to` check.

### 11. `Optional.get()` without presence check
`accountRepository.findById(...).get()` throws `NoSuchElementException` → surfaces as a 500 for a
client typo. Same for `transferRepository.findById(...).get()` in the idempotency branch.
**Fix:** `orElseThrow(() -> new AccountNotFoundException(id))` → mapped to 404.

### 12. Generic `RuntimeException` + catch-all in controller that leaks internals
`new RuntimeException("Insufficient funds")` can't be distinguished from a real bug; the controller
catches `Exception` and returns **500 with a stack-trace element** in the body. Client errors (400/404/
409/422) all become 500s, and you leak class/method names to the internet.
**Fix:** domain exceptions (`InsufficientFundsException`, `AccountNotFoundException`, …) +
`@RestControllerAdvice` mapping them to proper status codes with a safe error body.

### 13. `Map<String, Object>` request body + manual casting
Unsafe casts (`ClassCastException` if a client sends a number for `note`), no schema, no docs.
**Fix:** typed request/response DTOs (records), don't return the JPA entity directly either (entity
leaks internals, couples API to DB schema).

### 14. Notification runs on an un-managed `ExecutorService`
- `Executors.newFixedThreadPool(10)` is created per bean, never shut down → threads leak on context
  reload / tests.
- Fire-and-forget after the DB write but **not after commit** (once you add `@Transactional`, the
  notification can go out for a transfer that then rolls back) → use `@TransactionalEventListener(AFTER_COMMIT)`
  or an **outbox table** + scheduler (you use db-scheduler at work — say that).
- If the notification fails the customer is never told and nobody retries; `e.printStackTrace()`
  goes to stderr, not the logger, no metrics.
**Fix:** Spring `@Async` with a configured `TaskExecutor`, or transactional outbox for guaranteed
delivery; log with SLF4J + correlation id.

### 15. `NotificationClient` — resource leaks and unsafe JSON
- `OutputStream` and `BufferedReader` never closed (no try-with-resources) → connection/fd leak.
- No connect/read timeout → a slow notifications service hangs a pool thread forever; with 10 threads
  the pool is exhausted and notifications silently stop.
- JSON built by string concatenation: a `note` containing `"` breaks the payload — and a note with
  `","admin":true` is **injection** into the downstream service.
- `HttpURLConnection` instead of `RestClient`/`WebClient`; response status never checked.
- `System.out.println` instead of a logger.
**Fix:** `RestClient` with timeouts, Jackson for serialisation, proper logging, check status, maybe
Resilience4j retry/circuit breaker.

### 16. `history()` loads the whole table into memory
`findAll()` then filter in Java → O(n) over all transfers of all customers, every request. Will fall
over at scale.
**Fix:** `findByFromAccountIdOrToAccountId(id, id)` (or a `@Query`), with `Pageable`, and an index on
both columns. Same for `totalSent`: do `SUM(amount)` in the DB (`@Query("select coalesce(sum(t.amount),0) …")`).

### 17. `t.getStatus() == "COMPLETED"` — string comparison with `==`
Works only by accident of string interning; breaks the moment the value comes from the DB.
Symptom: `totalSent` returns 0.
**Fix:** `.equals`, or better, make `status` an `enum TransferStatus` with `@Enumerated(STRING)`.

### 18. Status as a magic string, `java.util.Date`
`"PENDING"`/`"COMPLETED"` literals in two places; `status` set to `COMPLETED` before the money
movement has actually committed. `new Date()` instead of `Instant`/`OffsetDateTime` via an injected
`Clock` (untestable, timezone-ambiguous).
**Fix:** enum + `Instant createdAt`, inject `Clock`.

### 19. Missing DB constraints / schema concerns
`Transfer` has no `requestId`, no `@Version`, no index, `ddl-auto=create-drop` in `application.properties`
(fine for dev, catastrophic if that file ships to prod — ask how profiles are managed). No audit
trail: a transfer should be an immutable ledger entry, double-entry style, not a mutable balance
column alone. (Senior-level comment: "I'd model this as a ledger with entries, and balance as a
derived/materialised value.")

### 20. No observability
No logging of the transfer with ids, no metrics, no correlation id. A support ticket "my money is
gone" can't be investigated.

---

## 🟢 Nits — mention 2–3 max, don't spend time here

- `Account` is mutable with public setters (`setBalance`) → anyone can bypass invariants; prefer
  `debit(amount)` / `credit(amount)` domain methods that enforce `>= 0`.
- `MAX_TRANSFER` hard-coded; should be config.
- `totalSent` endpoint path uses camelCase (`totalSent`); REST convention is kebab-case
  (`/accounts/{id}/total-sent`).
- `history` takes `accountId` as a query param while `totalSent` uses a path variable — inconsistent
  resource design; both should be `/accounts/{id}/transfers`.
- `show-sql=true` is noisy in prod.
- Unused imports, `final Transfer saved = transfer` reassignment dance.
- Controller returns `ResponseEntity<Object>` — lose the type.
- No API docs (OpenAPI).

---

## What to *say* in the interview (60-second version)

> "Overall this needs changes before merge — the feature idea is fine and the structure
> (controller → service → repo) is clean, but there are three things I'd block on.
> First, the money movement isn't atomic or locked: no `@Transactional`, and a read-check-write on
> the balance, so a failure mid-way or two concurrent requests can lose or double-spend money.
> Second, money is a `double` — I'd switch to `BigDecimal` or minor units.
> Third, idempotency is an in-memory `HashMap`, so after a deploy a client retry creates a duplicate
> transfer; it needs a unique constraint in the DB. Beyond those: a live API key is in the source
> and needs rotating, there's no ownership check so anyone can transfer from any account, and the
> tests don't assert anything — the insufficient-funds test passes even if no exception is thrown.
> I'd also move the notification to after-commit via an outbox so we never notify about a
> rolled-back transfer."

Then stop and let them ask. If they say "anything else?", go to validation (negative amount =
reverse transfer, same account), frozen/currency checks, `findAll()` in `history`, and the `==`
string comparison.

## Questions an interviewer may follow up with

1. Optimistic vs pessimistic locking — when would you choose which? What happens under retry?
2. How does `@Transactional` actually work (proxies, self-invocation, propagation, isolation levels,
   `readOnly`)? What isolation level do you need here?
3. How would you make the notification reliable? (outbox pattern, at-least-once + idempotent consumer)
4. How would you implement idempotency keys across multiple instances? What's the TTL story?
5. Why `BigDecimal` and not `double`? What scale/rounding for EUR?
6. How would you test the race condition? (two threads, `CountDownLatch`, Testcontainers Postgres)
7. What would you add before this goes to production? (metrics, alerts, feature flag, rate limiting,
   audit log)
