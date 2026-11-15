# Code review practice — Scalable Capital prep

Start with `SCALABLE_RESEARCH.md` — what candidates report about the round, their stack, and how
they grade. Then do the exercises. Each has a `ANSWER_KEY.md` next to it with severity-ranked
findings, the fix, the 60-second spoken summary, and the follow-up questions to expect.

| # | Folder | Mirrors what candidates reported | Time |
|---|---|---|---|
| 3 | `exercise3-deprecated-and-tests/` | "deprecated libraries and broken tests" — Spring service + test class | 10 min |
| 4 | `exercise4-forex-api/` | "query vs path params, then design forex endpoints" — REST controller | 10 min |
| 5 | `exercise5-kotlin-portfolio/` | Kotlin snippet (their primary language) — coroutines, nullability, money | 10 min |
| 2 | `exercise2-concurrency/` | "review concurrency classes" — plain Java cache | 10 min |
| 1 | root `src/` + `PULL_REQUEST.md` | full PR review dress rehearsal — transfers between accounts | 20 min |

Suggested order: 3 → 4 → 5 → 2, then exercise 1 under a strict 20-minute timer the day before.

## The method (same every time)

1. **Intent (1 min)** — "What does this do, and what would make it wrong for a broker?"
   Money precision · atomicity · concurrency · idempotency · authorization · auditability.
2. **Blockers (4 min)** — scan for those six before reading line by line.
3. **Line by line (4 min)** — error handling, resources, validation, N+1, `==`, deprecated APIs,
   tests that can't fail.
4. **Verdict (1 min)** — request changes / approve; top 3; one thing you liked; one question.

## Phrases that land well in a review interview

- "Before I go line by line — the thing I'd want to be sure about in a broker is X. Let me check that first."
- "This is the one I'd block on, because in production it means ___."
- "This is fine as a first iteration, but I'd ask the author to ___ before merge."
- "I'd leave this as a nit — not worth holding the PR for."
- "What's the intended behaviour when ___? The code and the PR description disagree."
- "I'd approve once the transaction boundary and the tests are fixed; the structure is good."

## Mistakes that lose the round

Reading silently for minutes · listing nits first · "I'd add logging" before "this double-spends" ·
no verdict · never asking the author a question · claiming something is wrong without being able to
say what breaks.

The root project (exercise 1) is a real Maven build: `mvn test` (Java 21). Exercises 2–5 are
single files meant to be read, not built.
