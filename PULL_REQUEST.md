# PR #482: Add cash transfer between accounts

**Author:** jonas.m
**Reviewers:** you
**Branch:** feature/cash-transfers → main

## Summary

Adds the ability for customers to move cash between their own accounts (e.g. from the
cash account to a savings plan account). Also exposes transfer history and a
"total sent" endpoint that the mobile team asked for.

- `POST /api/transfer` – executes a transfer. Clients send a `requestId` so retries are safe.
- `GET /api/transfer/history?accountId=…` – all transfers involving the account
- `GET /api/accounts/{id}/totalSent` – sum of completed outgoing transfers

After a successful transfer we notify the owner via the notifications service
(done in the background so it doesn't slow down the request).

## Testing

Unit tests added for the happy path and insufficient funds. Both green locally.

## Notes

Kept it simple for now, we can add currency conversion later.

---

## How to practise

1. Set a timer for **20 minutes** (interviews give you ~15 min for this part).
2. Read the diff the way you would on GitHub: `pom.xml`, then `src/main/java/...`, then the test.
3. Write your review into `MY_REVIEW.md` using the template. For each finding say:
   - **where** (file + line/method),
   - **what's wrong**,
   - **why it matters** (what breaks in production),
   - **severity**: 🔴 Blocking / 🟠 Should fix / 🟢 Nit,
   - **how you'd fix it** (one line).
4. Then say out loud, in 60 seconds, what you'd tell the author: top 3 blockers, overall
   verdict (approve / request changes), and what you liked.
5. Only then open `ANSWER_KEY.md` and compare.

Hint for the interview: start with "What is this code trying to do, and what would make
it wrong for a fintech?" — money, concurrency, idempotency, data integrity, security —
before you go line by line. Interviewers care more about *prioritisation* than about
finding every nit.
