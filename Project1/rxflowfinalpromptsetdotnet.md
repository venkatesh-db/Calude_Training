# RxFlow — Final Prompt Set (.NET stack)
## The full progression: build once, then four levels of participant expertise

**Stack substitution used throughout this document** (swap if your org
standardizes on something else — the lab design does not depend on
these exact libraries, only on each one filling the same role as its
Python original):

| Role in original (Python) | .NET equivalent used here |
|---|---|
| FastAPI + Pydantic v2 | ASP.NET Core 8 Web API + C# record DTOs + Data Annotations/FluentValidation |
| Plain service layer | Plain C# service layer (POCOs, no framework magic) |
| SQLAlchemy 2.x | Entity Framework Core 8 |
| Alembic | EF Core Migrations (`Up()`/`Down()` — a direct match for Alembic's upgrade/downgrade shape) |
| Redis (cache + locks) | StackExchange.Redis |
| Celery workers | `BackgroundService`/`IHostedService` consumers on Confluent.Kafka for event-driven work, plus Quartz.NET (or Hangfire) for the time-based surfacing/coating jobs — together covering what Celery covered |
| Kafka via Redpanda | unchanged — Kafka via Redpanda, client via Confluent.Kafka |
| pytest / pytest-asyncio / hypothesis / testcontainers | xUnit, FluentAssertions, FsCheck (property-based testing), Testcontainers for .NET |
| ruff, mypy --strict, bandit, pip-audit | Roslyn analyzers + StyleCop, nullable reference types with `TreatWarningsAsErrors`, Security Code Scan (the bandit role), `dotnet list package --vulnerable` (the pip-audit role) |
| Docker Compose, OpenTelemetry | unchanged |
| Build tool | `dotnet` CLI / MSBuild |

Run in this order. Each level is a fresh session unless stated otherwise.

---

## Setup — you run this once, before Day 1

Claude Code, Docker available. Not chat.

```
Role: you are acting as principal architect for a corporate training
lab, not as the participants. Build RxFlow, a .NET teaching
repository. This is a training artefact, not a product. Every design
choice serves the lab exercise described at the end.

DOMAIN
Prescription-to-lens order and lab routing. An optician submits a
prescription and frame choice. The system validates the prescription
against what a lens can physically be ground to, prices it, routes the
job to an Rx lab by capability and live load, schedules surfacing and
coating, and tracks to shipment.

STACK
ASP.NET Core 8 Web API (C#) + FluentValidation · plain C# service
layer · PostgreSQL with Entity Framework Core 8 and EF Core Migrations ·
Redis via StackExchange.Redis for cache and locks · Kafka consumers via
`BackgroundService` (Confluent.Kafka) for event-driven background work,
Quartz.NET for time-based jobs (surfacing/coating scheduling) —
together filling the role Celery filled · Kafka via Redpanda · xUnit,
FluentAssertions, FsCheck, Testcontainers for .NET · Roslyn analyzers +
StyleCop, nullable reference types with TreatWarningsAsErrors, Security
Code Scan, `dotnet list package --vulnerable` · Docker Compose ·
OpenTelemetry (.NET SDK).

WHAT LAB 1 WILL ASK PARTICIPANTS TO ESTABLISH
Build the repo so each of these has a real, findable answer:

  1. Module map and service boundaries — at least 5 projects/modules
     with genuine boundaries, and at least one boundary that is
     violated somewhere.
  2. Full request path for POST /orders — controller → request record →
     service → repository (EF Core) → Kafka event/consumer. Deep
     enough that tracing it takes effort, shallow enough to finish in
     90 minutes.
  3. Build, test, lint, static-analysis commands — these must ACTUALLY
     RUN. Include at least one command in the README that is stale and
     fails, so "verified by running" differs from "inferred from docs".
  4. Schema and migration state — EF Core Migrations with 4-6
     migrations, one of which has an empty (no-op) `Down()` method.
  5. Redis and background-worker usage — Redis used as both cache and
     lock, with an in-flight counter updated by read-modify-write, not
     atomically. A background consumer or scheduled job with a Polly
     retry policy that amplifies load on timeout.
  6. Outbound calls and timeouts — 3-4 external calls (via
     `HttpClient`/`IHttpClientFactory`). At least one with no timeout
     configured at all. At least one with a Polly retry policy that
     compounds with a caller's retry.
  7. Auth and authorization model — token auth (ASP.NET Core JWT
     middleware) on most routes, and one endpoint (lab override) with
     no authorization check.
  8. Where PII reaches the logs — prescription values and patient
     identifiers emitted in at least two places, one obvious in a log
     call (`ILogger`), one indirect via a record's default `ToString()`
     or a serialized log payload.
  9. Five highest-risk files — make risk genuinely uneven: one large
     untested class (pricing, ~900 lines, three copies of the discount
     rule), one concurrency-sensitive class, one with raw/inline SQL.
 10. Unknowns — leave 2-3 things genuinely undeterminable from the code
     alone (e.g. which layer owns retry responsibility), so an honest
     report has to say "cannot determine".

SEEDED DEFECTS
Ten, surfacing across the week. Participants are not told what they are:
  1. Duplicate lens job on retry of a slow submit — no idempotency
  2. Prescription validation silently clamps out-of-range cylinder/axis
  3. Lab routing selects by static priority, ignores live capacity
  4. Polly retry policy on the consumer/scheduler amplifies load ~3x on
     connector timeout
  5. Redis in-flight counter drifts under concurrency
  6. Patient identifiers and prescriptions in application logs
  7. Lab-override endpoint has no authorization check
  8. 900-line pricing class, no tests, three copies of the discount rule
  9. EF Core migration with an empty `Down()` and no documented manual
     rollback procedure
 10. Ad-hoc reporting query built with string interpolation (`$"..."`)
     — SQL injection

DESIGN RULES
- The test suite must PASS while all ten defects are live. For each
  defect, tell me which test looks like it should have caught it and
  why it does not.
- Concurrency defects need realistic work inside the window — a pricing
  call, an external request. Never a Task.Delay()/Thread.Sleep().
- No comment anywhere hints that code is intentionally wrong. No TODO,
  no "note: race here". The code must read as ordinary work under
  deadline.
- Each defect must be either reproducible on demand or demonstrable
  with a one-line command.
- Realistic mess: inconsistent naming across modules, one dead class,
  one abandoned refactor half-applied. Undocumented does not mean clean.
- README must be plausible and partly wrong — the kind a team writes
  once and stops updating.

DELIVERABLES — three separate outputs, do not mix them

1. THE PARTICIPANT REPOSITORY
   The codebase itself. No hints, no answer keys, no diagrams.

2. INSTRUCTOR.md — write this OUTSIDE the repo folder, in a sibling
   directory, not inside the participant tree.
   For each of the 10 defects: file and line range, the one-line
   reproduction command, expected output when it fires, which existing
   test looks like it should catch it and why it does not.

3. INSTRUCTOR-DIAGRAMS.md — also outside the repo folder.
   Exactly two diagrams, as Mermaid: one system design (whole repo,
   mark the violated boundary), one code flow (POST /orders end to
   end, mark every defect sitting on this path). Not one diagram per
   defect — that is Lab 1's deliverable, not yours to pre-solve.

ACCEPTANCE — do not present anything until all hold
- docker compose up brings the stack up on a clean machine
- dotnet test (full suite) passes with all ten defects live
- Roslyn analyzers/StyleCop pass, and the build is clean under nullable
  reference types with TreatWarningsAsErrors
- each reproduction command in INSTRUCTOR.md fails on 3 consecutive
  runs, output pasted
- POST /orders works end to end against the running stack
- both diagrams render as valid Mermaid, annotations point at real
  file:line
- you paste the actual output of every command above, not a summary

Iterate until acceptance is met. Do not ask me between attempts.
Report at the end: what failed on the way, and what you changed.
```

**Before Day 1 starts:** move `INSTRUCTOR.md` and `INSTRUCTOR-DIAGRAMS.md`
out of the folder that gets handed to participants. A read-only agent
sitting in the same directory can still open them.

---

## Level 1 · Execute — Lab 1 (Day 1)

Participants run this in a fresh session, on the clean repo.

```
Read-only. Do not modify anything.

I have never seen this repository before. Give me a map I can act on.

Cover:
  - what each module is responsible for, one line each
  - the data model: tables, columns, constraints
  - every place a database connection or transaction is opened
  - all outbound calls and their timeout behaviour
  - what the test suite asserts, and what it does not
  - the three files I should read first, and why

Every claim needs a file and line range. Mark each CONFIRMED
(you read it) or HYPOTHESIS (you inferred it).

End with: what you did not open, and what you are unsure about.
```

---

## Level 1 · Execute — Lab 2 (Day 1)

Fresh session. Read-only, no fix.

```
Read-only. Do not modify any file. Do not write code. Do not propose
a fix.

Domain: prescription-to-lens ordering. A duplicate lens job means a
lab cuts two lenses for one prescription — wasted material, a second
charge, and a shipment nobody asked for.

Original ticket, verbatim: "Opticians say some orders go through
twice when the site is slow. Please fix."

This is not a task contract. It is a symptom description. Your job is
to turn it into one, without touching any code.

Evidence:
  - src/RxFlow.Api/Controllers/OrdersController.cs
  - src/RxFlow.Application/Services/OrderService.cs
  - src/RxFlow.Workers/OrderEventsWorker.cs
  - src/RxFlow.Infrastructure/Data/Entities/ (EF Core entities),
    src/RxFlow.Infrastructure/Migrations/
  - evidence/support-tickets-duplicate-orders.md (if present)

Produce a task contract with exactly these sections. For every claim,
give file and line range, and mark CONFIRMED or HYPOTHESIS.

1. FAILURE SYMPTOMS AND REQUEST LIFECYCLE
   Trace POST /orders end to end. At which exact step can a second
   request create a second row for the same prescription?

2. RETRY OWNERSHIP — THIS IS HALF THE BUG
   Does the client retry? Does the gateway or load balancer retry?
   Does the Kafka consumer or its Polly retry policy retry? Answer
   each separately, from the code, not from assumption. If more than
   one layer could be retrying the same request, say so explicitly —
   do not pick one and move on.

3. IDEMPOTENCY KEY DESIGN
   Is there currently any idempotency key, header, or constraint?
   If none exists, do not design one yet — state precisely what is
   missing and where it would need to be checked.

4. DATABASE TRANSACTION BOUNDARIES AND REDIS CONSISTENCY
   Where does a transaction open and close around order creation
   (an EF Core `DbContext` SaveChanges scope, or an explicit
   transaction)? Is Redis used here at all — as a lock, a cache, a
   counter? Is any read-modify-write on Redis non-atomic?

5. CONCURRENCY AND TIMEOUT BUDGET
   List every operation between the first read and the final write.
   Sum their timeouts. State the total window during which a second
   request could land.

6. EXPECTED THROUGHPUT
   From the code or config, what request volume is this endpoint
   built for? If this is not determinable from the repo, say so and
   state what you would need — do not estimate.

7. REGRESSION TESTS AND ROLLBACK EXPECTATIONS
   Does any existing test cover concurrent submission? Name it, or
   state none exists. What would need to roll back cleanly if a fix
   here caused a new problem?

Close with:
  - UNKNOWNS: anything you could not determine from this repo alone,
    and what evidence would resolve each one
  - RECOMMENDED INVESTIGATION ORDER: which of the seven sections to
    resolve first, and why

Do not resolve section 2 by guessing. If the code allows more than
one honest answer, that ambiguity is your most important finding —
report it, do not collapse it.
```

**Instructor checkpoint here.** Review each task contract before Lab 3.
This is the plan-mode approval gate — cheapest place to catch a wrong
direction.

---

## Level 1 · Execute — Lab 3 (Day 1)

New session, after you approve the Lab 2 contract.

```
Approved task contract from Lab 2 (paste it here):
<paste the approved contract>

Constraints:
- Minimal patch. Fix only what the approved contract identifies.
- No float/double in money paths (decimal only), no PII in logs, no
  new outbound call without an explicit timeout.
- Do not touch retry logic in a layer the contract did not name as
  the owner.

Work through this in stages. Stop after each and wait for me, except
where told to continue automatically.

1. REPRODUCE
   Write a script or test that creates a duplicate order under
   concurrent retries. It must fail reliably — run it 3 times and
   paste all three outputs. If it does not fail 3/3, tell me what
   you changed before trying again.

2. WRITE THE FAILING TEST FIRST
   A regression test that fails against current code and will pass
   once idempotency is enforced. Show me it failing.

3. LOCATE THE RACE WINDOW
   Confirm, against the running reproduction, the exact span between
   first read and final write. Does it match what Lab 2 predicted?
   If not, say so explicitly — the contract may have been wrong, and
   that is a valid finding, not a failure.

4. EXAMINE TRANSACTION BOUNDARIES
   Where does a transaction currently open and close? Is this where
   the idempotency check belongs, or does it need to move?

Stop here. I will approve before you implement.

--- after my approval ---

5. IMPLEMENT the minimal fix per the approved plan.

6. VALIDATE
   - Regression test passes. Paste real output.
   - Reproduction passes 3/3. Paste all three runs.
   - Full suite still passes. Paste real output.

7. VALIDATE ROLLBACK
   If this fix is reverted, does the system return to its exact prior
   state? What happens to orders created while the fix was live, if
   it is later rolled back?

8. MEASURE PERFORMANCE IMPACT
   What does the idempotency check cost per request? Give a number.
   If you cannot measure it here, say what you would need to.

Report: commands run, exact output, files changed, what you could
not verify.
```

---

## Level 2 · Author — Day 3

```
Read-only. Do not modify any file. Do not write code.

Work through this in five stages.

STAGE 1 — Read the code flow and provide code flow diagram

Each service's flow needs to be understood. Provide me a code flow
diagram as a new .md file and a rendered .html file.

STAGE 2 — category of issue

For each service, identify: 1. memory/resource leak 2. what a
profiler would flag 3. GC/allocation pressure 4. business logic
defect 5. an unoptimised search/sort or other algorithm. Provide me a
new .md file.

STAGE 3 — chain of issues

If one code fix is made, which other code depending on it would be
impacted? Trace the chain of links between services and across the
overall project folder structure.

STAGE 4 — domain specific issues

Find 10 domain-specific issues.

Domain: prescription-to-lens ordering. A duplicate lens job means a
lab cuts two lenses for one prescription — wasted material, a second
charge, and a shipment nobody asked for.

STAGE 5 — find all the current bugs, future bugs, scenario bugs

Create a new .md file to present them.
```

Give participants this instruction and nothing else. No prompt template.

```
There is a defect somewhere in this repository related to:
"Retries on the order-events background worker seem to be amplifying
load when a lab connector times out."

You have no other information.

Using the discipline from Labs 1-3 — evidence before conclusions,
CONFIRMED vs HYPOTHESIS, no fix before diagnosis, a task contract
before implementation — investigate this yourself. Write your own
prompts. Produce your own task contract. Get it approved. Fix it.

You will be assessed on the prompts you write, not only the fix
you produce.
```

**What to grade:** did they state stakes before asking a question? Did
they demand file:line for every claim, unprompted? Did they forbid the
fix before asking for the diagnosis? If yes to all three without being
told, the four moves have become instinct.

---

## Level 3 · Catch — Day 4

Prepare this once, before the session: ask Claude to produce an X-ray
report on a defect participants have not yet seen, and deliberately
instruct it to include exactly one confident, plausible, wrong claim
(a line number off by a few, or a "no test covers this" that is
actually false). Do not tell participants which claim is wrong.

**Prompt you run to generate the flawed report (instructor only):**

```
Produce a repository intelligence report on the lab-override
authorization defect, in the same format as Lab 1.

Deliberately include exactly one confident, specific, wrong claim
somewhere in the report — a line number that is slightly off, or a
statement that a test covers something it does not. Make it read as
plausible as every true claim around it. Do not mark it, hint at it,
or make it stand out in tone. Everything else in the report must be
accurate and properly cited.

Tell me separately, in a section I will remove before showing
participants, exactly which claim is the planted error and why it is
wrong.
```

**Prompt participants receive (no mention this contains an error):**

```
Here is a repository intelligence report someone else produced on the
lab-override authorization endpoint.

Verify it. Open at least three citations at random and confirm they
say what the report claims. Ask how any count or figure was obtained
and re-run the command yourself. If you find anything the report
claims that the code does not actually support, say exactly what and
where.

Do not assume the report is correct because it is well-written.
```

**Debrief question:** how long did it take to find it, and what made
you check that specific claim rather than another? The answer to the
second question is usually the real lesson — most people check the
claim that "felt" slightly off, and the discussion of *why* it felt
off is where the tacit skill becomes explicit.

---

## Level 4 · Institutionalize — Day 5

```
Look back across everything from this week: every correction you gave
me more than once, every constraint you had to restate, every mistake
I made that you had to catch.

List each one as a single operational rule — a command, a prohibition,
or a required check. Not an explanation, not a principle. Something
that could sit in a CLAUDE.md file and be followed without further
context.

Group them under: build and test commands, non-negotiable rules,
report format.

Then tell me: of everything you listed, which three would have saved
the most time this week if they had existed on Monday morning?
```

**What this produces:** a first-draft CLAUDE.md written from a week of
lived friction rather than guessed in advance. The closing question
forces prioritization — not everything belongs in a file that's loaded
on every single request.

---

## The one thing to track across all four levels

A visible cycle-time board: time from "given a defect" to "verified
fix," per participant, per level. Level 1 will be slow and
evidence-heavy. By Level 3 the same discipline should be faster, not
slower — good evidence habits stop producing false starts. That curve,
shown to the room, is the proof that rigor makes people quicker rather
than being an academic tax.
