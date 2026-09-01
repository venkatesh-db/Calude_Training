# Investigation Handoff — API 500s, 2026-08-17 (Java service)

Source: `investigation-report-2026-08-17.md` (log-only pattern analysis, no source code was opened, no root cause confirmed). This prompt asks you to take the CONFIRMED facts below and go find the root cause in the Java codebase/config. Please keep the same CONFIRMED vs. HYPOTHESIS discipline in whatever you report back.

## What's already confirmed from the logs (do not re-derive, just use as ground truth)

- 1,847 total 500s between **19:12:01 and 21:40:00** on 2026-08-17.
- **1,767 of those** are `connection pool timeout: could not acquire connection from pool (size=20, active=20) after 30000ms`, split across `/orders` (1,594), `/pricing/quote` (150), `/labs/health` (23).
- **64** are `InvalidOperation: decimal conversion failed for discount_rate`, all on `/pricing/quote`, all fast-failing (duration_ms=135, not a timeout) — a separate, unrelated bug from the pool exhaustion.
- **16** are `lab connector read timeout after 4000ms`, all on `/orders`, between 19:30–21:36.
- **900×** `db pool utilisation high` WARN lines starting at **19:04:13** (`waiting=17`, climbing to 30+), 8 minutes before the first 500, continuing until 21:44:59.
- A **deploy** landed at **19:08:41**: `version=2026.08.17-r3 previous=2026.08.14-r1`, 4 minutes before the first error.
- **Recovery** at 21:41:03: `db pool resized size=60 previous=20 applied_by=oncall`, 1 minute after the last 500.
- No correlation/request/trace ID in the logs, so nothing links a specific `/pricing/quote` failure to a specific `/orders` failure, and the deploy→pool-pressure causal link is **not provable from logs alone**.

## What you're being asked to do

1. Confirm whether the connection pool size is hard-coded/configured at **20** somewhere in this service (matches `size=20` in the timeout messages) — e.g. HikariCP `maximumPoolSize`, or an equivalent JDBC/R2DBC pool config. Check whether the `2026.08.17-r3` deploy touched this config, connection lifecycle code, or anything that could hold connections longer (a new blocking call inside a transaction, a missing timeout on an outbound call made while a connection is checked out, etc.).
2. Diff or review what actually shipped in `2026.08.17-r3` vs `2026.08.14-r1`. The WARN ramp starts *before* the deploy (19:04:13 vs 19:08:41), so don't assume the deploy is the trigger — check whether something else (traffic pattern, a scheduled job, an upstream dependency) started around 19:04 too.
3. Find the `discount_rate` decimal-conversion path on `/pricing/quote` (likely `BigDecimal.valueOf`/`new BigDecimal(String)` or Jackson deserialization of that field). These are fast failures (135ms), not pool-related — look for a bad/unexpected input format (empty string, locale-formatted number, null) rather than a pool issue.
4. Look at the outbound "lab connector" HTTP client used from `/orders` — find where its read timeout is set to 4000ms and check whether it's a shared client/thread pool that could itself contribute to connection exhaustion under load, or whether it's an independent, unrelated flaky dependency.
5. Report back explicitly labeling each conclusion CONFIRMED (you read the code/config and verified it) or HYPOTHESIS (plausible but unverified), the same way the log report did. Don't propose a fix until the cause is confirmed in code, per the original investigation's scope.

## Reference

Full log-based report and raw evidence file (`api-2026-08-17.log`) are in the same folder as this prompt if you need to cross-check counts or specific example lines.
