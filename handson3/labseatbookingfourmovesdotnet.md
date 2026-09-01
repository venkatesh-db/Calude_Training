# Lab: The Four Moves of a Strong Prompt (.NET stack)
## A seat sold twice — concurrency in seat inventory

**Duration:** 60–75 minutes
**Repository:** `seatbook-dotnet/`
**Prerequisite:** none. This works as the first prompting lab of Day 1.

**Why this defect:** the bug is invisible to a green test suite, cannot
be reproduced by clicking, and is explained by a pattern sitting in a
support file that has no code in it. Every one of the four moves earns
its place here.

---

## Setup

```bash
tar -xzf seatbook-dotnet.tar.gz
cd seatbook-dotnet
dotnet test                                   # 10 passed
dotnet run --project fixtures/ConcurrentBookFixture
                                               # FAIL - one seat sold 4 times
```

Run both before the lab starts, on the projector. Ten green tests and a
seat sold four times, from the same codebase, thirty seconds apart. The
room is now paying attention.

---

## The scenario

Intercity bus seat inventory. A seat is available when no booking row
exists for that trip and seat number. Booking checks availability,
prices the seat, and inserts the row.

Ten support escalations since 4 August: more than one passenger holding
a confirmed, paid ticket for the same seat. Two passengers refused
travel at the boarding point.

### The seeded defect

Participants are not told this.

`src/SeatBook.Booking/SeatBookingService.cs` — `BookSeat()` calls
`IsAvailable()` at line 43, calls `FareFor()` at line 47, and inserts at
line 52. Three separate connections (a new `SqlConnection`/`DbContext`
per call), no transaction, no lock. Classic check-then-act.

The fare call sits **inside** the window and widens it to a few hundred
milliseconds, which is why the defect only appears under real
concurrent load.

There is also **no unique constraint** on `(TripId, SeatNo)` in the
schema managed by `src/SeatBook.Booking/BookingRepository.cs`. The
application check is the only defence, and it is not atomic.

---

## Part 1 — Run the weak prompt first (5 minutes)

```
There's a double booking bug in the seat booking code. Fix it.
```

Expect: a confident patch adding a `lock` statement or a unique
constraint, with no reproduction, no reference to the escalation
pattern, and no comment on the existing rows already in production.

Ask the room: **how do we know this fix works?** Nothing was
reproduced, so nothing can be verified. The patch may even be correct —
which is what makes it dangerous to sign off.

---

## Part 2 — The strong prompt

```
Read-only. Do not modify any file. Do not add a lock.

Domain: intercity bus seat inventory. A seat sold twice means a
passenger with a paid confirmed ticket is refused boarding at the
stand. This is a revenue and reputation event, not a data glitch.

Symptom: ten support escalations since 4 August, multiple confirmed
bookings for one seat on one trip.

Evidence:
  - evidence/support-escalations-2026-08-15.md
  - src/SeatBook.Booking/SeatBookingService.cs
  - src/SeatBook.Booking/BookingRepository.cs

Task: trace the full path of BookSeat() and identify every point at
which two concurrent requests for the same seat can both succeed.

For each candidate give:
  - file and line range
  - the exact interleaving of two requests that produces it
  - what the database contains at each step of that interleaving
  - CONFIRMED (you can point at the code) or HYPOTHESIS (you inferred it)

Then, separately:
  - Does the existing test suite cover this? Name the test that would
    have caught it, or state that none exists.
  - Support says they cannot reproduce it manually. From the code,
    explain why not.

Do not propose a fix. Do not write code. If you cannot determine
something from the evidence, say what you would need.
```

---

## The four moves, and why each line is there

### Move 1 — State the role and stakes

> *"a passenger with a paid confirmed ticket is refused boarding at the
> stand"*

Stakes in human terms, not technical ones. Without this the model
treats it as an ordinary uniqueness question and produces a generic
answer. With it, the report gets written for someone who has to explain
this to an operator.

**Prevents:** a technically correct answer that misses why it matters.

### Move 2 — Give evidence, not description

> *three named files, and a support escalation document*

Note what is **not** listed: `FareCalculator.cs`. A strong answer
discovers that the fare lookup sits between the check and the insert
and widens the window. Listing it would have handed over the finding
instead of letting it be earned.

**Prevents:** invention. Without a real file the model produces a
plausible race condition it has seen in other codebases.

### Move 3 — Constrain the output shape

> *"the exact interleaving of two requests"*
> *"what the database contains at each step"*
> *"CONFIRMED or HYPOTHESIS"*

"There is a race condition" tells you nothing. *Request A checks,
request B checks, both see zero, both insert* is a claim you can check
against the code line by line.

**Prevents:** three fluent paragraphs that cannot be verified or acted
on.

### Move 4 — Forbid the shortcut

> *"Do not add a lock. Do not propose a fix. Do not write code."*

A lock is probably the right fix. But proposing it at this stage means
nobody has established what should happen to the second request,
whether existing duplicate rows need cleaning, or whether the fix
belongs in the database or the application.

**Prevents:** an unreviewable patch to an unproven diagnosis.

---

## Two questions that carry the lab

### "Does the existing test suite cover this?"

All 10 tests pass. `DoubleBooking_IsRejected` looks like exactly the
right test — and it is sequential, so it proves nothing about
concurrency.

Discovering that a green suite is **blind** is a better lesson than
finding the race itself. For a testing audience it is the whole point
of the session.

### "Explain why support cannot reproduce it."

The window is a few hundred milliseconds. A human clicking twice will
never hit it. Only sold-out evening departures generate enough
simultaneous requests — which is exactly why the escalations cluster on
21:00 to 22:30 trips.

This forces the model to connect **code** to a **pattern in a support
file**. That connection is what a senior engineer does and a junior one
does not.

---

## Expected findings

| Finding | Level |
|---|---|
| `IsAvailable()` at line 43 and `INSERT` at line 52 are not atomic | CONFIRMED |
| Three separate connections, no transaction spanning them | CONFIRMED |
| `FareFor()` at line 47 sits inside the window and widens it | CONFIRMED — requires opening `FareCalculator.cs`, which was not listed |
| No unique constraint on `(TripId, SeatNo)` in the schema | CONFIRMED — the structural finding |
| `DoubleBooking_IsRejected` is sequential and cannot catch this | CONFIRMED |
| Evening sold-out trips produce the concurrency; manual clicking cannot | HYPOTHESIS from code, supported by the escalation pattern |
| Existing production rows are already duplicated and need identifying | Correctly raised as out of scope for this stage |

---

## Grading rubric

| Level | What it looks like |
|---|---|
| **Weak** | "Race condition in BookSeat, add a lock." No interleaving, no line numbers, ignores the tests and the escalation pattern. |
| **Adequate** | Correct interleaving with line references. Marks claims CONFIRMED or HYPOTHESIS. Notices the tests are sequential. |
| **Strong** | All of the above, plus: finds `FareCalculator.cs` unprompted and explains that it widens the window, spots the missing unique constraint, connects the evening-departure pattern in the support file to the code, and raises the already-duplicated production rows as a separate decision. |

Grade on **what was cited**, not on whether the diagnosis is right. A
participant who reaches the correct answer without evidence has guessed
correctly, which is not a repeatable skill.

---

## Discussion to close (10 minutes)

**"The tests were green. What is a green suite worth?"**

Let the room sit with it. The answer is not "nothing" — it is that a
test suite proves only what it asserts, and this one asserts nothing
about two things happening at once. Concurrency defects are invisible
to sequential tests by construction, not by oversight.

**"What would you have to change to catch this in CI?"**

They will arrive at a concurrency test — several threads (or tasks),
one seat, one assertion on the row count. Which is exactly
`ConcurrentBookFixture.cs`, already in the repo, not wired into the
suite. That gap is the real finding of the lab.

**Bridge to the next module:** the fix is not a better prompt. It is a
concurrency test in CI and a unique constraint in the schema. Prompting
well told you what was missing. It could not add it for you.

---

## Instructor notes

**Do not list `FareCalculator.cs` in the prompt.** The strongest single
differentiator between participants is whether they find it. Handing it
over destroys the exercise.

**Expect someone to propose the fix anyway.** Put their answer next to
a compliant one and ask which they would defend in a post-incident
review.

**If a participant asks whether a unique constraint or a lock is
correct** — that is the right question, and the honest answer is that it
depends on what should happen to the losing request, and on whether the
production table can even accept the constraint given the duplicate
rows already in it. Neither is answerable from the code alone.

**The reproduction is not deterministic.** Thread scheduling varies, so
the count differs between runs — typically 2 to 5 duplicates from 12
concurrent requests (the fixture fires the requests with
`Task.WhenAll` over `Parallel.ForEachAsync` or similar, lined up behind
a `Barrier`/`SemaphoreSlim`). If a participant sees only one booking,
run it again with a higher concurrency count. That variability is
itself worth naming: this is what makes concurrency defects hard to pin
down.

**.NET-specific trap worth flagging:** if `SeatBookingService` resolves
its `DbContext` as scoped-per-request in a web host, running the
fixture as a bare console app instead can accidentally serialise calls
onto a single `DbContext` instance (which is not thread-safe and will
throw rather than race). Make sure the fixture creates one connection
per simulated request, the same way the real API does, or the race
won't reproduce for the wrong reason.

---

*Coderrange · corporate training and engineering capability*
