# Answer key — PriceCache (exercise 2, concurrency)

Time box: 10 minutes. This is the kind of "review these concurrency classes" snippet candidates
report at Scalable.

## 🔴 Blocking

1. **`HashMap` shared across threads** — request threads call `getPrice` (`put`) while the `Timer`
   thread runs `refresh` (`put`). Concurrent writes to `HashMap` can corrupt internal buckets; in
   old JDKs this caused infinite loops, today you get lost entries or
   `ConcurrentModificationException` from `keySet()` iteration during a `put`.
   → `ConcurrentHashMap`.

2. **`refreshing` flag is a non-atomic check-then-act and not `volatile`** — two threads can both
   see `false` and both refresh; and without `volatile`/synchronisation one thread may never see
   the other's write (visibility, Java Memory Model). → `AtomicBoolean.compareAndSet(false, true)`
   in a `try/finally` that resets it, or just drop the flag and make refresh idempotent.

3. **Broken double-checked singleton** — `getInstance` is not synchronised; two threads can create
   two instances, each with its own `Timer` thread. Also `instance` isn't `volatile`, so a thread
   may see a non-null but not-yet-fully-constructed object. In Spring you don't need this at all:
   let the container own the singleton. If you must: holder idiom or `synchronized` + `volatile`.

4. **`refreshing = false` is never reached if `fetchPrice` throws** — one exception and the cache
   never refreshes again; `TimerTask` exceptions also **kill the `Timer` thread** permanently.
   → `try/finally`; use `ScheduledExecutorService` (or Spring `@Scheduled`) which survives task
   exceptions; catch and log inside the task.

5. **Iterating `prices.keySet()` while `put`-ing into the same map** — even single-threaded that's
   a CME risk for a non-concurrent map; and with `ConcurrentHashMap` it's fine but you're still
   doing N sequential network calls on one thread every 5 s (will exceed the 5 s period as N grows,
   and `Timer` tasks then pile up). → batch fetch, or parallelise with bounded executor, or
   fetch-on-expiry per key instead of a global refresh.

## 🟠 Should fix

6. **Cache stampede / duplicate fetch in `getPrice`** — N requests for a cold ISIN all miss and all
   call the provider. → `prices.computeIfAbsent(isin, client::fetchPrice)` (note:
   `computeIfAbsent` on `ConcurrentHashMap` must not block long or re-enter the map; for slow
   remote calls, consider Caffeine with `loadingCache` which handles this properly).

7. **No TTL / staleness semantics on a cache miss** — a price fetched at second 4 and refreshed at
   second 5 is fine, but nothing marks an entry's age; `getLastRefresh` is global. For trading, the
   consumer needs to know *how old* a price is. → store `(price, timestamp)` or use Caffeine
   `expireAfterWrite`.

8. **`Double` for prices** — same `BigDecimal` argument as exercise 1; also `null` can be stored if
   the client returns `null`, then every later `get` re-fetches.

9. **`synchronized getLastRefresh` but unsynchronised write** — locking only the reader gives no
   visibility guarantee for the writer; it's half a fix. → `volatile long` or `AtomicLong`.

10. **`Timer` thread is never cancelled** — leaks on shutdown/context reload; `Timer` is a
    single thread, not daemon by default, can keep the JVM alive. → `@PreDestroy` shutdown, or
    Spring `@Scheduled(fixedRate = 5000)`.

11. **Unbounded growth** — every ISIN ever requested stays forever. → max size + eviction.

## 🟢 Nits

- Private constructor + static instance + constructor doing work (starting threads) → hard to test;
  no way to inject a fake clock or executor.
- `size()` on a concurrent map is approximate — fine, but don't use it for decisions.
- Name `client` for `MarketDataClient`; the interface nested inside the cache is odd.

## The 30-second summary

> "The core problem is a plain `HashMap` and a plain boolean shared between request threads and a
> background `Timer` thread — that's a data race on the map and a visibility problem on the flag.
> I'd swap in `ConcurrentHashMap`, make the refresh guard an `AtomicBoolean` with try/finally, and
> replace `Timer` with `@Scheduled` or a `ScheduledExecutorService` so one exception doesn't kill
> refreshing forever. Then I'd question whether we should hand-roll this at all — Caffeine gives us
> TTL, bounded size and stampede protection out of the box."

## Likely follow-ups

- What does `volatile` guarantee and what doesn't it? (visibility + ordering, not atomicity of
  `i++`)
- `synchronized` vs `ReentrantLock` vs `ReadWriteLock` vs `StampedLock` — when?
- How does `ConcurrentHashMap` achieve thread safety? (per-bin CAS + synchronized on bin head;
  `computeIfAbsent` atomicity; weakly consistent iterators)
- What's the difference between `Timer`, `ScheduledExecutorService` and virtual threads for this?
- How would you test this concurrency bug? (`ExecutorService` with 100 tasks hitting `getPrice`,
  `CountDownLatch` to start them together, assert provider called once per ISIN)
