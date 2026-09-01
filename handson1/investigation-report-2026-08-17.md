# Investigation Report — API 500s, 2026-08-17

Evidence: `api-2026-08-17.log` (36,130 lines). Read-only inspection. No source code opened. No fix proposed — this is a pattern report only.

## 1. Understand

**Being asked:** a read-only, evidence-based pattern analysis of the 500s from yesterday evening, so no one has to guess at a cause — counts, time window, grouping by endpoint and error message, top groups with real example lines, and a look at WARN/INFO lines in addition to ERROR. Every claim labeled CONFIRMED (counted) or HYPOTHESIS (inferred), with the exact command shown.

**Not being asked:** a root-cause diagnosis, a fix, or any reasoning over source code.

## 2. Inspect

### Volume and window
- **Total 500s: 1,847** — CONFIRMED
  `grep -c 'status=500' api-2026-08-17.log`
- **Window: 19:12:01 → 21:40:00** — CONFIRMED
  `grep 'status=500' api-2026-08-17.log | head -1` / `| tail -1`
  First: `19:12:01.485 ERROR [api-2b91] POST /orders status=500 duration_ms=30046 msg="connection pool timeout: could not acquire connection from pool (size=20, active=20) after 30000ms"`

### Top 3 groups by endpoint
| Endpoint | Count |
|---|---|
| `/orders` | 1,610 |
| `/pricing/quote` | 214 |
| `/labs/health` | 23 |

Command: `grep 'status=500' api-2026-08-17.log | grep -oE '(GET|POST|PUT|DELETE) [^ ]+' | awk '{print $2}' | sort | uniq -c`
CONFIRMED — counted directly.

### Top groups by error message (cross-tabbed against endpoint)

- **connection pool timeout — 1,767 total** (1,594 `/orders`, 150 `/pricing/quote`, 23 `/labs/health`). CONFIRMED.
  `grep -c 'connection pool timeout' api-2026-08-17.log`
  Example: `19:12:01.485 ERROR [api-2b91] POST /orders status=500 duration_ms=30046 msg="connection pool timeout…"`

- **InvalidOperation: decimal conversion failed for discount_rate — 64**, all on `/pricing/quote`. CONFIRMED.
  Example: `19:14:18.554 ERROR [api-2b91] POST /pricing/quote status=500 duration_ms=135 msg="InvalidOperation: decimal conversion failed for discount_rate"`
  **Not the same failure as the pool timeout** — duration_ms=135 (fast fail) vs. duration_ms≈30000 (pool timeout). The 214 `/pricing/quote` errors are two unrelated mechanisms (150 + 64) that happen to share a time window, not one homogeneous group.

- **lab connector read timeout after 4000ms — 16**, all on `/orders`, all between 19:30–21:36 (inside the incident window). CONFIRMED count/window. Whether it shares a cause with the pool timeouts is HYPOTHESIS — nothing in the file links it directly.
  Example: `19:30:27.191 ERROR [api-5a08] POST /orders status=500 duration_ms=7345 msg="lab connector read timeout after 4000ms"`

### WARN and INFO lines (not only ERROR)

- **900× `db pool utilisation high`** WARN lines, starting **19:04:13** (`waiting=17`) — **8 minutes before the first 500**. `waiting=` climbs across the window (17 → 30 → 33 → …) and the WARNs continue until 21:44:59, a few minutes after the last error. CONFIRMED (`grep -c 'pool utilisation high'`, sampled with `awk 'NR%150==1'`).

- **One `deploy complete` line at 19:08:41** — `version=2026.08.17-r3 previous=2026.08.14-r1`, 4 minutes before the first error, sitting between the start of the WARN ramp and the first ERROR. CONFIRMED existence/timing. Causal link to the pool pressure is HYPOTHESIS — the file shows temporal proximity only.

- **One recovery line at 21:41:03** — `db pool resized size=60 previous=20 applied_by=oncall`, 1 minute after the last 500. CONFIRMED existence/timing. Consistent with the pool-size theory but does not itself prove it — HYPOTHESIS for causation.

- **Celery retry WARNs** (`notify_lab`, `submit_lab_job`, 210 total) and **slow-query / redis-latency WARNs** exist but are spread across the entire day (08:08–23:54), not concentrated in the incident window. CONFIRMED via timestamp distribution. Read as background noise unrelated to this incident — flagged HYPOTHESIS, not ruled out with certainty.

### Limiting gap

**No correlation/request/trace ID field anywhere in the file** — CONFIRMED (`grep -ic 'correlation\|request_id\|trace_id'` → 0). This means a specific `/pricing/quote` failure cannot be linked to a specific `/orders` failure, and the deploy cannot be proven to have caused the pool pressure from this evidence alone. Any statement of that form stays HYPOTHESIS.

## 3. Plan

*Not yet completed — stage 3 was not run in the conversation this report was generated from.*
