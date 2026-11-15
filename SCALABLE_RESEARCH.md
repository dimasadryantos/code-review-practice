# What Scalable Capital's code review round actually looks like (research, Nov 2026)

## Format reported by candidates (Glassdoor, 2024–2026)

- **45 min, one interviewer (senior backend engineer):** "experience, general technical questions,
  plus a review of a piece of code they provide." (Senior SWE, Munich, Sep 2026)
- "Technical interview happened for 40 mins … they asked multiple technical review questions. I
  answered and then went into code challenge. **I was asked to review a pull request.**"
- "**They give a piece of code and want you to comment on it.** 2 – Talk about the tools and other
  things you've worked with. 3 – Designing an exchange service (system design, talking)."
- "Reviewing code. Easy screening (**using deprecated libraries and broken tests**)."
- "Capitalizing the first letter of each word in a sentence. **Review concurrency classes.**"
- Tech questions reported alongside: "concurrency, scalability and **data integrity with high
  concurrency**", "how annotations work internally in Spring Boot", "difference between **query
  parameters and path parameters** in REST, then design example endpoints for a **forex module**".
- Take-home (Codility) when it exists: Spring Boot `@Controller` CRUD + writing test cases for a
  given method → they care about **tests** a lot.
- Several reviewers say the coding task is easy; the differentiator is what you say about the code.

## Their stack (from current job ads, Trading/Banking/Broker platform teams)

Kotlin + Java, Spring Boot, PostgreSQL/MySQL + DynamoDB, AWS (ECS/Fargate/Lambda), Terraform,
gRPC/protobuf as a plus, event-driven distributed systems, DDD, "strict controls and auditability".
→ The snippet you get will most likely be **Kotlin or Java with Spring**, domain = orders, trades,
prices, accounts, FX. Be ready to read Kotlin even if you answer in Java terms.

## So the code they hand you will probably contain some mix of:

| Category | What to look for | Reported? |
|---|---|---|
| Concurrency | shared `HashMap`, non-volatile flags, check-then-act, `synchronized` on wrong thing, `Timer`, un-shut-down executors, `GlobalScope.launch` / `runBlocking` in Kotlin | ✅ explicitly |
| Data integrity | no `@Transactional`, read-modify-write on balances, no optimistic/pessimistic lock, no unique constraint for idempotency | ✅ explicitly |
| Deprecated APIs | `new Date()`, `SimpleDateFormat` shared, `new URL(String)`, `Integer(int)`, `Thread.stop`, `RestTemplate`-style old clients, `@MockBean` (deprecated Boot 3.4), JUnit 4 in a JUnit 5 project | ✅ explicitly |
| Broken tests | `assertTrue(true)`, try/catch swallowing the expected exception, `@Disabled` without reason, `Thread.sleep` for async, tests depending on each other / order, mocking what you're testing | ✅ explicitly |
| Money | `double`/`Float` for amounts, `==` on floating point, no currency, no scale/rounding | fintech default |
| REST design | verbs in paths, path vs query params misuse, wrong status codes, non-idempotent PUT, GET with side effects, no pagination, returning entities | ✅ (forex endpoints question) |
| Error handling | catch-all `Exception`, swallowed, generic `RuntimeException`, stack traces in HTTP responses | common |
| Security | hard-coded secrets, missing authz on account ids, string-built JSON/SQL | common |
| Spring specifics | `@Transactional` on private/self-invoked method, field injection, `@Autowired` in tests, circular deps, `@Async` inside same class | ✅ (annotations question) |

## How they grade (inferred from reviews + how this format works generally)

1. **Prioritisation** – did you lead with the bug that loses money / corrupts data, or with naming?
2. **Reasoning** – can you explain *why* it's wrong and *what happens in prod*, not just "this is bad"?
3. **Fix** – do you know the idiomatic fix (and its trade-off)?
4. **Communication** – would the author feel helped or attacked? Verdict given? Something positive said?
5. **Depth probes** – after you name an issue they go one level down ("how does `@Transactional`
   actually do that?", "optimistic or pessimistic here, and why?").

## Exercises in this repo mapped to the reports

| Exercise | Mirrors |
|---|---|
| 1 `PR #482 transfers` | "review a pull request", data integrity under concurrency, broken tests |
| 2 `PriceCache` | "review concurrency classes" |
| 3 `deprecated-and-tests` | "deprecated libraries and broken tests" |
| 4 `forex-api` | "query vs path params, design forex endpoints" |
| 5 `kotlin-portfolio` | Kotlin snippet — their primary language |

Do them in order 3 → 4 → 5 (new, ~10 min each), then re-do 1 under time as the dress rehearsal.
