# Trace: concurrent double-booking in bookSeat()

## Note on scope and a discrepancy in the evidence set

This task names `evidence/support-escalations-2026-08-15.md` as evidence. That
file does not exist anywhere in this repository — I searched the full tree
and found no `evidence/` directory prior to this report. I cannot cite it and
have not fabricated its contents. If the escalation detail (dates, seat/trip
IDs, ticket count) needs to be in this report, I need that file provided.

Second, `src/main/java/com/bus/booking/db/Database.java` currently defines
`bookings` with `CONSTRAINT uq_bookings_trip_seat UNIQUE (trip_id, seat_no)`,
and `BookingRepository.insert()` currently catches the resulting constraint
violation (SQLState class `23`) and throws `SeatAlreadyBookedException`,
which `SeatBookingService.bookSeat()` catches and turns into
`SeatUnavailableException`. That guard is present in the code as read right
now. It was added in an earlier turn of this same session (see `AI_LOG.md`,
T1) and is not reflected in the repo's history — there is no `.git` directory
here to date it independently.

Everything below traces the code as it stands. Where the DB-level constraint
now closes a path, I say so explicitly and also describe the interleaving as
it would play out **without that constraint** (i.e. schema as `CREATE TABLE
bookings (... no UNIQUE ...)`), since that is the shape the support
escalations describe and the shape the test suite was written against. I
flag each one accordingly.

---

## Call path of bookSeat()

`SeatBookingService.bookSeat()` — `src/main/java/com/bus/booking/SeatBookingService.java:36-55`

```
36  bookSeat(tripId, seatNo, passengerName, peakHour)
37      validate(...)                          // no I/O
40      repository.isAvailable(tripId, seatNo)  // connection #1, opened+closed
45      fareFor(tripId, seatNo, peakHour)        // Thread.sleep(150), no DB I/O
49      repository.insert(...)                   // connection #2, opened+closed
```

Three separate calls, three separate JDBC connections (per the class
docstring on `BookingRepository`, `db/BookingRepository.java:10-14`: "Every
method pulls its own connection from the pooled DataSource and releases it
immediately"). Nothing in `bookSeat()` synchronizes across these three
calls — no lock, no shared transaction, no `synchronized` block. There is
no application-level mutex anywhere in `SeatBookingService` or
`BookingRepository` (confirmed by reading both files in full; neither
imports `java.util.concurrent.locks` or uses the `synchronized` keyword).

---

## Candidate 1 — check-then-act gap between isAvailable() and insert()

**File / lines:** `SeatBookingService.java:40` (the check) through `:49` (the
act), with the 150 ms sleep at `:45`/`fareFor()` (lines 57-69) sitting in
between.

**Status:** CONFIRMED as a code-level race window. Whether it results in two
rows in the current schema depends on the schema state (see below) —
**CONFIRMED for the no-unique-constraint schema, closed by the currently-read
unique constraint** (which downgrades the outcome from "two rows" to "one row
+ one `SeatUnavailableException`" — see Candidate 1a).

**Interleaving (two requests, R1 and R2, same tripId/seatNo):**

| t | R1 | R2 | `bookings` table state |
|---|----|----|----|
| 0 | `isAvailable(T,S)` → conn#1 opens, runs `SELECT COUNT(*)...`, returns `0` | — | empty for (T,S) |
| 1 | — | `isAvailable(T,S)` → conn#3 opens, runs the same `SELECT COUNT(*)...`, returns `0` | still empty — R1 hasn't written anything yet |
| 2 | passes the `if (!repository.isAvailable(...))` guard at line 40, enters `fareFor()`, sleeps 150 ms | passes the same guard | still empty |
| 3 | wakes from sleep, computes `fare` | wakes from sleep, computes `fare` | still empty |
| 4 | `repository.insert(T,S,"Asha",fare)` → conn#2 opens, `INSERT` executes, conn#2 closes | — | **one row for (T,S)** |
| 5 | returns `new Booking(...)` — caller sees success | `repository.insert(T,S,"Ravi",fare)` → conn#4 opens, `INSERT` executes | |

At step 5, R2's insert is the second write for the same `(trip_id, seat_no)`.
- **Without the unique constraint:** the `INSERT` at `BookingRepository.java:44-51` has no `WHERE NOT EXISTS` guard and no constraint to violate — it succeeds unconditionally. R2 also returns a `Booking` object to its caller. `bookings` now has **two rows** for the same trip/seat. This is the "seat sold twice" state a support ticket describes: two passengers each hold a `Booking` object (and, presumably upstream of this code, a paid confirmation) for one physical seat.
- **With the unique constraint** (current code): R2's `INSERT` at step 5 raises a `SQLException` with SQLState `23xxx`. `BookingRepository.insert()` (`db/BookingRepository.java:52-57`) catches it, and because `isUniqueViolation()` (`:60-64`) returns true, throws `SeatAlreadyBookedException`. `SeatBookingService.bookSeat()` (`:48-52`) catches that and rethrows `SeatUnavailableException` to R2's caller. `bookings` ends with **one row**. I call this out as **Candidate 1a** below since it's a materially different outcome from Candidate 1, using the identical interleaving.

The interleaving requires only that both `isAvailable()` reads land before
either `insert()` write — nothing more exotic than ordinary thread
scheduling under load. The 150 ms `fareFor()` sleep (explicitly documented at
`:57-62` as existing to widen this exact window "so the check-then-act
window in bookSeat() is wide enough to matter under real concurrent load")
makes the window wide relative to the two ~sub-millisecond `SELECT`s that
precede it, which is why it reproduces reliably under concurrent load and
essentially never under manual, serial use (see "why support can't
reproduce it" below).

## Candidate 1a — same window, current schema, downgraded outcome

**File / lines:** same as Candidate 1, plus `db/Database.java:42-51` (the
`UNIQUE (trip_id, seat_no)` constraint) and `db/BookingRepository.java:38-58`
(insert + violation handling).

**Status:** CONFIRMED, as read right now. This is not a double-booking — it
is the same race window as Candidate 1, but the loser gets
`SeatUnavailableException` instead of a second confirmed `Booking`. I list it
separately because it's the same interleaving table as above; only the last
row differs, per the branch described there.

**Caveat on how I know this closes the bug:** I have not executed the
concurrency fixture in this turn (this task is read-only). The claim that
the unique constraint prevents two committed rows rests on standard ANSI SQL
unique-constraint semantics (a second matching `INSERT` inside a
default-isolation, autocommitting connection is rejected) plus H2's SQLState
`23*` numbering, which is what `isUniqueViolation()` checks for
(`db/BookingRepository.java:60-64`). I did not independently verify H2's
exact SQLState value for this constraint type in this turn — flagging this
as **HYPOTHESIS on the specific SQLState value**, though the general
unique-constraint-blocks-duplicate-insert mechanism is CONFIRMED SQL
semantics, not something specific to this codebase.

---

## Candidate 2 — isAvailable() itself, read-modify gap within the read

**File / lines:** `BookingRepository.java:23-36`

**Status:** Considered and ruled out as a *separate* source of double
booking. `isAvailable()` only reads (`SELECT COUNT(*)`); it performs no
write and holds no lock, so there's no independent race entirely inside this
method — its only contribution to double-booking is as the "check" half of
Candidate 1's check-then-act gap, already covered there. Listing it
separately would double-count the same defect.

---

## Candidate 3 — two inserts, no check-then-act, i.e. both requests skip past isAvailable() before either read committed anything

**Status:** This is the same interleaving as Candidate 1 (see the table: R2's
`isAvailable()` at t=1 reads before R1's `insert()` at t=4 — the two requests
never need to overlap in time at every step, only R2's read must precede
R1's write). I don't count it as a distinct candidate; it's a restatement of
Candidate 1 with the emphasis on "R2 doesn't even need to be sleeping in
`fareFor()` when R1 writes — R2 only needs to have already read `0` before
R1's write lands." Calling it out because it's the more general form of the
race: any timing where the second `isAvailable()` precedes the first
`insert()` reproduces it, not just the specific "both sleeping
simultaneously" framing.

---

## Any other path into insert()?

I checked for other callers of `BookingRepository.insert()` or
`.isAvailable()` in the source tree:

```
grep -rn "repository.insert\|repository.isAvailable\|\.insert(\|\.isAvailable(" src/main
```

The only call sites are the two in `SeatBookingService.bookSeat()` already
traced (`:40` and `:49`). No other service, controller, or scheduled job
calls into `BookingRepository` in `src/main`. **CONFIRMED** — this is the
only path into `insert()`, so there is no second, independent code path that
could also produce a double sale.

---

## Does the existing test suite cover this?

**No test would have caught it — CONFIRMED, by reading every test in
`src/test/java/com/bus/booking/SeatBookingServiceTest.java`.**

The class-level Javadoc says it outright (`SeatBookingServiceTest.java:14-19`):

> "All tests here run sequentially against a single thread, so the
> check-then-act race in SeatBookingService never has a chance to trigger.
> The suite is green even though the service is unsafe under real
> concurrent load."

The closest test, `rejectsSecondBookingOfSameSeat()`
(`SeatBookingServiceTest.java:52-57`), calls `bookSeat()` twice
**sequentially on the calling thread**:

```java
service.bookSeat("TRIP-1", 5, "Asha", false);
assertThrows(SeatUnavailableException.class,
        () -> service.bookSeat("TRIP-1", 5, "Ravi", false));
```

The first call fully completes — including its `insert()` — before the
second call's `isAvailable()` ever runs. That ordering makes the check
at line 40 correct by construction; it can never observe the race because
there is no concurrency in the test at all. This test would pass identically
whether or not the check-then-act gap, or the unique constraint, existed.

The only test in the repo that actually exercises concurrent callers is
`ConcurrentBookFixture` (`src/main/java/com/bus/booking/fixtures/ConcurrentBookFixture.java`)
— and it is not a test: it's a standalone `main()` fixture, run manually via
`exec:java`, not wired into `mvn test` or any `@Test` method, so it does not
run as part of the suite and would not fail a CI build or block a merge on
its own.

## Why can't support reproduce it manually?

From the code: the race requires two `isAvailable()` reads to land before
either request's `insert()` write, and the only thing that widens that
window enough to hit reliably is concurrent load — specifically, two
requests whose `fareFor()` 150 ms sleeps (or just their two `isAvailable()`
calls) overlap in wall-clock time. A support agent testing manually — one
person, one browser tab, one booking attempt after another — cannot produce
overlapping requests: each `bookSeat()` call runs to completion (all three
DB round trips plus the 150 ms sleep) before the next one starts, which is
exactly the `rejectsSecondBookingOfSameSeat()` ordering above, and that
ordering cannot lose the race — R2's `isAvailable()` will correctly see R1's
already-committed row. The defect only manifests when two *independent*
concurrent callers (e.g. two passengers hitting "confirm" within the same
~150 ms+ window against the live system) interleave, which a single person
testing serially cannot generate no matter how fast they click.

---

## Summary table

| Candidate | Lines | Confirmed / Hypothesis | Produces 2 committed rows? |
|---|---|---|---|
| 1: check-then-act, `isAvailable()` (`:40`) → `insert()` (`:49`) | `SeatBookingService.java:40-49` | CONFIRMED (code path); outcome depends on schema — CONFIRMED to yield 2 rows only under a no-unique-constraint schema | Yes, under a schema without the unique constraint |
| 1a: same window, current schema | `SeatBookingService.java:48-52`, `BookingRepository.java:38-58`, `Database.java:42-51` | CONFIRMED path taken; HYPOTHESIS on exact SQLState value not independently re-verified this turn | No — second request gets `SeatUnavailableException` |
| 2: inside `isAvailable()` alone | `BookingRepository.java:23-36` | Ruled out — not a separate source, folds into Candidate 1 | N/A |
| 3: general form of Candidate 1 (no simultaneous sleep needed) | same as Candidate 1 | CONFIRMED, same defect, wider framing | Same as Candidate 1 |

## What I'd need to go further

- `evidence/support-escalations-2026-08-15.md` itself, to cross-check the
  specific trip/seat IDs and dates in the tickets against a specific
  interleaving, and to confirm whether the escalations predate or postdate
  the unique-constraint change referenced above.
- Confirmation of which schema was actually live in production when the ten
  escalations occurred, since the code I can read right now already carries
  a fix for Candidate 1's original (2-row) outcome — I can't tell from the
  source tree alone whether that fix has shipped anywhere support-facing.
