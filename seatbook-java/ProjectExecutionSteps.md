# Project Execution Steps

Chronological summary of everything done in this session on `seatbook-java`.
For per-turn file-level detail see `AI_LOG.md`; this document is the
narrative walkthrough.

---

## Step 1 — Diagnose and fix the double-booking bug

**Ask:** "There's a double booking bug in the seat booking code. Fix it."

- Located the relevant code: `SeatBookingService`, `BookingRepository`,
  `Database`, `SeatUnavailableException`, plus their test/fixture
  counterparts.
- Root cause found by reading `SeatBookingService.bookSeat()`: a
  check-then-act race. `isAvailable()` and `insert()` each opened and
  closed their own JDBC connection with no shared transaction or lock
  between them, and a 150ms simulated pricing call (`fareFor()`) sat in
  between, widening the window. Two concurrent requests for the same seat
  could both pass the availability check before either had written a row.
- Fix implemented:
  - Added a `UNIQUE (trip_id, seat_no)` constraint to the `bookings` table
    in `Database.java`, making the database — not the earlier read — the
    actual arbiter.
  - Added `SeatAlreadyBookedException` (new file) and had
    `BookingRepository.insert()` catch the resulting SQLState `23xxx`
    constraint violation and throw it.
  - `SeatBookingService.bookSeat()` catches that and rethrows the existing
    public `SeatUnavailableException`, so the external API is unchanged.
- Verified: `mvn test` (all 10 pre-existing tests green) and 3 manual runs
  of `ConcurrentBookFixture` (4-way contention) — each run: exactly 1
  success, 3 rejections, 1 DB row for the contended seat.

## Step 2 — Create `AI_LOG.md`

**Ask:** "create ai log file to store the code files modified and to track
t1 t2 .. t3"

- Created `AI_LOG.md` at the repo root as a per-turn index: task summary +
  files modified, one entry per turn (T1, T2, ...).
- Backfilled `T1` (the double-booking fix above) and `T2` (creating the log
  itself).

## Step 3 — Read-only race trace / evidence report

**Ask:** a read-only forensic prompt: trace every point in `bookSeat()`
where two concurrent requests for the same seat could both succeed, cite
file/line ranges, mark each finding CONFIRMED or HYPOTHESIS, check test
coverage, and explain why manual QA couldn't reproduce it. Explicitly: no
fix, no code changes.

- Checked for the referenced evidence file
  (`evidence/support-escalations-2026-08-15.md`) — it did not exist
  anywhere in the repo; flagged this rather than inventing its contents.
- Noted the code already carried the Step 1 fix, and traced *both* states:
  the original no-constraint interleaving (produces 2 committed rows — what
  the support-ticket description implies) and the current
  constraint-guarded state (same race window, but the loser gets
  `SeatUnavailableException` instead of a second confirmed booking).
- Confirmed via `grep` that `SeatBookingService.bookSeat()` is the only
  caller of `BookingRepository.insert()`/`isAvailable()` in `src/main` — no
  other path into the bug.
- Established that no existing `@Test` could have caught the race:
  `SeatBookingServiceTest` runs everything on a single thread by
  construction (its own class Javadoc says so), and `ConcurrentBookFixture`
  is a manual `main()`, not wired into `mvn test`.
- Explained why support couldn't reproduce it manually: one person clicking
  serially can't produce the overlapping-request timing the race requires.
- Output: `evidence/bookSeat-race-trace.md`. No source files modified.

## Step 4 — Generated (not executed) an unrelated implementation spec

**Ask:** a second prompt template, this time for a "decimal-only, round-once-per-account, batch-total-must-match" accrual/rounding fix, with explicit instruction to generate but not execute it.

- Checked the repo for any accrual/rate/reconciliation code
  (`grep -rliE "accrual|rate|reconcil|batch" src`) — none exists;
  `seatbook-java` is bus seat booking/fare pricing only.
- Asked the user how to proceed given the scope mismatch; user chose
  "generate the template as-is."
- Produced `evidence/accrual-rounding-implementation-spec.md`: the
  constraints translated into Java/BigDecimal terms (e.g.
  `MidpointRounding.AwayFromZero` → `RoundingMode.HALF_UP`), with
  placeholder class names since no real accrual module exists in this
  repo, and a list of what's needed before it could actually be
  implemented here. Nothing executed, no code touched.

## Step 5 — Regression test for the already-fixed race, with a "don't modify the fix" constraint

**Ask:** re-run the double-booking fix/trace/validate/review/report
workflow, but with an explicit new constraint: check `AI_LOG.md` first, and
if the bug is already fixed, do not modify the code.

- Checked `AI_LOG.md` — confirmed T1 had already applied the fix.
- Flagged the conflict directly rather than silently resolving it: the
  request's "write a failing test first" assumes the bug is still live; it
  isn't, so no genuine RED phase was possible without violating the
  don't-modify-code constraint.
- Added `src/test/java/com/bus/booking/SeatBookingConcurrencyTest.java` — a
  real `@Test` (not a manual fixture) that races 8 concurrent bookers for
  one seat via `ExecutorService` + `CountDownLatch`, asserting exactly one
  success and exactly one DB row. Test-only change; no production code
  touched.
- Ran `mvn test`: 11 tests total (10 original + 1 new), all green, 0
  failures/errors — pasted the actual Maven output, not a paraphrase.
- Self-reviewed the diff: called out that the RED phase was impossible
  given the constraints, that the test proves single-JVM safety only (not
  multi-process/real-DB behavior), and named two unverified assumptions —
  H2's SQLState `23xxx` convention across versions, and that no other
  caller into `BookingRepository` exists (re-asserted from Step 3's grep,
  not re-run this turn).
- Reported commands run, files changed, and what remains unverified,
  including that `evidence/support-escalations-2026-08-15.md` still does
  not exist so the fix can't be correlated to the actual incident tickets.

## Step 6 — This document

**Ask:** summarize all steps taken in this project into
`ProjectExecutionSteps.md`.

- Created this file.

---

## Net state of the repo after all steps

**Production code changed (Step 1 only):**
- `src/main/java/com/bus/booking/db/Database.java`
- `src/main/java/com/bus/booking/db/BookingRepository.java`
- `src/main/java/com/bus/booking/db/SeatAlreadyBookedException.java` (new)
- `src/main/java/com/bus/booking/SeatBookingService.java`

**Tests added:**
- `src/test/java/com/bus/booking/SeatBookingConcurrencyTest.java` (Step 5)

**Docs/logs created:**
- `AI_LOG.md` (Step 2)
- `evidence/bookSeat-race-trace.md` (Step 3)
- `evidence/accrual-rounding-implementation-spec.md` (Step 4, generated only — not tied to any real code in this repo)
- `ProjectExecutionSteps.md` (this file, Step 6)

**Open items / not done:**
- `evidence/support-escalations-2026-08-15.md` was referenced by two
  separate prompts and never found in the repo — the actual incident
  tickets have not been correlated to the fix.
- The accrual/rounding spec (Step 4) is not implemented anywhere; it
  doesn't correspond to any real module in this codebase.
- Multi-process/real-database-level verification of the concurrency fix
  (beyond single-JVM H2) has not been done.
