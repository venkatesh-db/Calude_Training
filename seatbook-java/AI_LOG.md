# AI Log

Tracks work done by Claude Code in this repo, one entry per turn (T1, T2, ...).
Each entry lists the task and the files touched (created/modified).

## T1 — Fix double-booking race condition

Task: fix the double-booking bug in the seat booking code.

Root cause: `SeatBookingService.bookSeat` did check-then-act
(`isAvailable()` then `insert()`) with no shared transaction — each call
used its own pooled connection, leaving a race window where concurrent
bookings could both pass the availability check for the same seat.

Fix: enforce uniqueness at the database level (`UNIQUE (trip_id, seat_no)`)
and translate the resulting constraint violation into the existing
`SeatUnavailableException`, so the database — not the earlier read — is
the source of truth.

Files modified:
- `src/main/java/com/bus/booking/db/Database.java` — added `UNIQUE (trip_id, seat_no)` constraint to the `bookings` table DDL.
- `src/main/java/com/bus/booking/db/BookingRepository.java` — `insert()` detects unique-constraint violations (SQLState `23xxx`) and throws `SeatAlreadyBookedException`.
- `src/main/java/com/bus/booking/db/SeatAlreadyBookedException.java` — new exception (created).
- `src/main/java/com/bus/booking/SeatBookingService.java` — `bookSeat()` catches `SeatAlreadyBookedException` from `insert()` and rethrows the public `SeatUnavailableException`; updated class javadoc to describe `isAvailable()` as a fast-path only, not the correctness guard.

Verification:
- `mvn -q test` — all 10 existing unit tests pass.
- `mvn -q compile exec:java -Dexec.mainClass="com.bus.booking.fixtures.ConcurrentBookFixture"` — run 3x, each time exactly 1 success / 3 rejections / 1 DB row for the contended seat (previously could sell the seat more than once).

## T2 — Create this AI log file

Task: create an AI log file to track modified files per turn (T1, T2, T3, ...).

Files modified:
- `AI_LOG.md` — created (this file).
