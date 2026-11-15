# Answer key — Exercise 4: ForexController (REST design + "design forex endpoints")

A Senior candidate reported: "One of the central questions was the **difference between query
parameters and path parameters** in REST APIs. Then I was asked to **design example endpoints for a
forex module**." This exercise covers both.

## Part A — the one-liner they want first

**Path parameters identify a resource** (`/accounts/{id}`, `/rates/EUR-USD`): required, hierarchical,
part of the resource's identity. **Query parameters filter, sort, paginate or parameterise a
collection or a read** (`?from=2026-01-01&page=2&sort=date`): optional, non-hierarchical, order
doesn't matter. Rule of thumb: if removing it changes *which* resource you mean → path; if it changes
*how much / which subset / in what shape* → query. Never put secrets or PII in either (URLs are logged).

## Part B — review findings

### 🔴 Blocking

1. **`GET /forex/convert` moves money.** GET must be safe and idempotent: browsers prefetch, proxies
   cache, monitoring pings, the app retries on timeout → **duplicate conversions**. Also the amount
   and account end up in access logs and browser history. → `POST /accounts/{accountId}/conversions`
   with a JSON body and an **`Idempotency-Key` header**; return `201` + the conversion resource.

2. **`GET /forex/alert/delete`** — same issue, a destructive GET. → `DELETE /alerts/{id}` → `204`.

3. **`convert` returns `200 OK` with `"error: …"` in the body.** Clients can't distinguish success
   from failure without parsing a string; monitoring sees 100% success. → proper status codes
   (`400` validation, `404` account, `409`/`422` insufficient funds, `500` unexpected) via
   `@RestControllerAdvice` and an RFC 9457 `ProblemDetail` body. Never leak `e.getMessage()` raw.

4. **`double amount` / `double` rate** — money and FX rates as binary floating point. Rates need
   high precision (`BigDecimal`, scale 6–10), amounts need currency-specific scale (JPY 0, EUR 2,
   KWD 3). Serialise as string or fixed-decimal in JSON to avoid client-side float issues.

5. **No authorization.** `accountId` is taken from the query string; nothing checks the caller owns
   it. `GET /conversions` returns **every customer's** conversions with no admin check.
   → principal from security context; admin endpoints under a separate, role-guarded path.

### 🟠 Should fix

6. **Verbs in URLs** (`/getRate`, `/alert/delete`) → nouns + HTTP methods. `GET /rates?base=EUR&quote=USD`
   or `GET /rates/EUR-USD`.

7. **`POST /alert?id=…` doing create-or-update** — an upsert disguised as POST with a query param
   switching semantics. → `POST /alerts` (create, `201` + `Location`), `PUT /alerts/{id}` (full
   replace, idempotent) or `PATCH /alerts/{id}`. Returning the request body back as the response for
   update is lazy — return the stored resource.

8. **Date range in path segments** (`/rates/{from}/{to}/{start}/{end}`): a date range is a filter,
   not an identity → query params, with `LocalDate` typing and `@DateTimeFormat(iso = DATE)`, not
   `String`. Also: no pagination, no cap on range → a 20-year range dumps the table.

9. **`Map<String, Object>` request bodies and `List<Map<String,Object>>` responses** — no schema, no
   validation, no OpenAPI, unsafe casts. → DTOs/records with `@Valid`.

10. **Singular vs plural inconsistency** (`/alert` vs `/rates` vs `/conversions`), and the module
    name `/forex` as a root — resources should hang off the owning aggregate:
    `/accounts/{id}/conversions`, `/alerts`, `/rates`.

11. **No versioning / content negotiation** — `/v1` or header-based; not blocking but say it.

12. **Currency codes as free strings** — validate ISO 4217 (`Currency.getInstance`), uppercase,
    `from != to`.

13. **Rate as a scalar `double` in the body of `getRate`** → return an object: rate, base, quote,
    timestamp/`asOf`, source, maybe bid/ask. A rate without a timestamp is useless in finance.

14. **Caching headers** on quotes: `Cache-Control: max-age=…` or ETag, since rates are polled heavily.

### 🟢 Nits

- `ResponseEntity<Object>` / `ResponseEntity<String>` — type them.
- Comments describing the URL instead of OpenAPI annotations.
- `@RequestMapping("/forex")` + wildcard import.

## Part C — how to answer "design the forex endpoints" (talk for ~2 minutes)

```
GET    /v1/rates?base=EUR&quote=USD                      current quote → {base, quote, rate, asOf, source}
GET    /v1/rates/EUR-USD/history?from=2026-01-01&to=2026-02-01&granularity=day&page=0&size=100
GET    /v1/accounts/{accountId}/conversions?page=&size=  customer's conversion history (paginated)
POST   /v1/accounts/{accountId}/conversions              body {from, to, amount:"100.00", quoteId?}
                                                          headers Idempotency-Key → 201 + Location
GET    /v1/accounts/{accountId}/conversions/{conversionId}
POST   /v1/quotes                                         optional: lock a rate for N seconds → quoteId
GET    /v1/alerts   POST /v1/alerts → 201   GET/PUT/PATCH/DELETE /v1/alerts/{alertId}
```

Points to say while drawing it:
- Identity in the path, filters in the query; collection plural nouns; sub-resources under the
  owning account.
- **POST for conversion with idempotency key**, stored with the result; replay returns the same
  `201`/`200` body. Explain why: mobile retries on flaky networks.
- Money as decimal **strings** with explicit currency; rate with `asOf`.
- Two-step quote → execute if the business needs a guaranteed rate (lock quote for 30 s, execute
  by `quoteId`, `409` if expired). This shows product thinking.
- Status codes: `201` create, `200` read, `204` delete, `400/404/409/422`, `429` for quote
  hammering.
- Auth: account ownership enforced server-side; admin reporting lives under `/admin/...` with a
  different role, or better in a reporting service.
- Pagination (cursor-based for history), caching for rates, OpenAPI contract, versioning.

## Follow-ups to expect

- "PUT vs PATCH vs POST — which is idempotent and why?"
- "How do you implement the idempotency key?" (unique index on (account, key); store response;
  return stored response on replay; TTL)
- "What if the rate changes between quote and execution?"
- "Where does the conversion actually get applied atomically?" → back to transactions/ledger.
