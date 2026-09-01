# Lab: The Seven-Stage Loop on a Banking Defect (Java stack)
## Interest accrual reconciliation mismatch

**Duration:** 60–75 minutes
**Domain:** retail banking, daily interest accrual
**Prerequisite:** the staged log-analysis lab

**Why this defect:** the arithmetic is simple enough that every
participant follows it, but two of the seven stages surface decisions
that engineering is not allowed to make alone. That gap — between a
technically correct patch and an authorised one — is the lesson.

---

## The scenario

The nightly reconciliation batch fails. The ledger total does not equal
the sum of per-account interest. The gap is a few paise across roughly
50,000 accounts.

Small enough to ignore. Large enough to fail reconciliation every
night, and to become an audit finding if it persists.

### The seeded defects

Participants are not told these.

1. `src/main/java/com/bank/interest/AccrualService.java:47` computes with `double`
2. Line 52 rounds with `new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN)` — banker's rounding, half-to-even
3. `src/main/java/com/bank/ledger/PostingService.java:120` rounds the batch total *again*, after summing

The mismatch comes from rounding twice at different levels, compounded
by binary `double` representation.

---

## Prompt A — stages 1 to 3

Participants paste this and stop.

```
Work through this in stages. Stop after each and wait for me.

Domain: retail banking, daily interest accrual. Any change to
rounding or money representation is a regulated behaviour change,
not a refactor.

Symptom: the nightly reconciliation batch fails. The ledger total
does not equal the sum of per-account interest. The gap is a few
paise across ~50,000 accounts.

Evidence:
  - evidence/recon-fail-2026-08-16.txt (the batch diff)
  - src/main/java/com/bank/interest/AccrualService.java
  - src/main/java/com/bank/ledger/PostingService.java

1. UNDERSTAND — restate what I am asking, and what I am not.

2. INSPECT — read-only.
   Trace how one account's interest is computed and how the batch
   total is computed. For each step give:
     - file and line range
     - the numeric type used
     - whether rounding happens there, and with what rule
   Mark each CONFIRMED (you read it) or HYPOTHESIS (you inferred it).

3. PLAN — what you would change, what you would deliberately leave
   alone, and any decision that is NOT an engineering decision.

Do not write code. Stop at 3.
```

### The line that does the heavy lifting

> **"any decision that is NOT an engineering decision"**

Without it you get a clean technical plan that silently decides the
bank's rounding convention and quietly ignores historical drift. With
it, both surface before any code is written.

---

## Expected output from Prompt A

**Stage 1 — Understand**

> You want the batch total to equal the sum of per-account interest
> exactly. You are not asking me to change the interest rate table,
> the accrual schedule, or the reconciliation job itself.

**Stage 2 — Inspect**

> CONFIRMED — `AccrualService.java:47` uses `double`: `balance * rate / 365`
> CONFIRMED — `AccrualService.java:52` rounds using `RoundingMode.HALF_EVEN` (banker's rounding)
> CONFIRMED — `PostingService.java:120` rounds the batch total separately, after summing
> HYPOTHESIS — the mismatch comes from rounding at two levels rather than one. Not proven; I have not run the numbers.

**Stage 3 — Plan**

> Change: `BigDecimal` throughout the money path, `RoundingMode.HALF_UP`
> via `setScale(2, RoundingMode.HALF_UP)`, rounded once per account.
> Leave alone: rate table, schedule, reconciliation job.
>
> **Not engineering decisions:**
> - Historical balances were computed with the old logic. Correcting
>   them is a finance and compliance decision, not a code change.
> - `RoundingMode.HALF_UP` is conventional, but the mandated convention must
>   come from bank policy. I have not seen a policy document.

If the model surfaces those two items without being told to look for
them, the prompt worked.

---

## Prompt B — stages 4 to 7

Send only after the plan is approved.

```
Approved. Constraints for implementation:

- BigDecimal only. No double or float anywhere in the money path.
- RoundingMode.HALF_UP, quantised via setScale(2, RoundingMode.HALF_UP).
- Round once, per account. Never round the batch total.
- Do not change rates, schedule, or the reconciliation job.
- Historical balances are out of scope — future accruals only.

4. IMPLEMENT
   Write the failing test first. It must assert that the sum of
   per-account accruals equals the batch total exactly.
   Show me the test failing before you patch anything.
   Then the smallest patch that makes it pass.

5. VALIDATE
   Run the suite. Paste actual output, not a summary.
   Then run accrual over a 5,000-account fixture and show the
   batch delta. It must be exactly 0.00.

6. REVIEW
   Read your own diff. Tell me what a reviewer would object to,
   and name anything you assumed rather than verified.

7. REPORT
   Commands run · files changed · what remains unverified.
```

### The three constraints that matter most

**"Show me the test failing before you patch."**
Without this you get a test written after the fix, passing for reasons
nobody checked. In money code that is how a wrong assertion ships.

**"Batch delta must be exactly 0.00."**
A numeric acceptance criterion, not "reconciliation should work." The
model cannot claim success without producing the number.

**"Historical balances are out of scope."**
Stated as a boundary, so the agent does not helpfully write a backfill
script for 50,000 accounts — an unreviewed correction to customer money.

---

## The expected test

```java
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AccrualServiceTest {

    @Test
    void accrualSumsToBatchTotal() {
        List<BigDecimal> accounts = List.of(
            new BigDecimal("10432.17"),
            new BigDecimal("8891.44"),
            new BigDecimal("50120.09")
        );

        BigDecimal perAccountSum = accounts.stream()
            .map(balance -> AccrualService.accrue(balance, RATE))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(PostingService.batchTotal(accounts, RATE), perAccountSum);
    }
}
```

Fails today by 1 paisa. Passes after the patch. Roughly a dozen lines
changed across two files.

---

## Grading rubric

| Level | What it looks like |
|---|---|
| **Weak** | Finds the `double`, patches it, tests pass. Never mentions historical drift or the rounding policy. Technically correct, professionally incomplete. |
| **Adequate** | Finds all three defects including the double rounding. Correct `BigDecimal` patch with a failing-first test. Marks the causal explanation as hypothesis until the numbers are run. |
| **Strong** | All of the above, plus: surfaces historical drift as a finance decision, flags `RoundingMode.HALF_UP` as requiring policy confirmation, and asks in stage 6 whether any downstream report depends on the old batch-rounding behaviour. |

Grade on what the participant **escalated**, not on whether the tests
pass. A patch that passes and silently changes a regulated behaviour is
worse than no patch.

---

## Discussion to close (10 minutes)

Ask the room: *which of the seven stages could an agent have done
alone, unattended?*

The honest answer is 1, 2, 4, 5 and 7. Stages 3 and 6 both produced
decisions that belong to finance and compliance. That is not a
limitation of the model — it is the correct division of authority, and
it is what an automation roadmap has to be built around.

**Bridge to the MCP and governance module:** this is why the write path
in an enterprise MCP server requires human approval. The agent can
investigate, draft and validate. It cannot be the one who decides that
a regulated behaviour changes.

---

## Instructor notes

**Run Prompt A alone first**, and read only the "not an engineering
decision" section aloud. If the model surfaced historical drift and the
rounding convention unprompted, participants see what evidence-first
prompting actually buys — not faster code, but the questions they would
have missed.

**Expect at least one participant to skip Prompt A.** Their patch will
work. Put it on the screen next to a strong one and ask the room which
they would sign off on before a regulator.

**If someone asks whether `RoundingMode.HALF_UP` is correct** — that is
the right question, and the honest answer is that it depends on the
bank's documented policy and the applicable regulation. Neither you nor
the model can answer it from the code.

**Java-specific trap worth flagging:** `Math.round()` on a `double`
rounds half-up, not half-to-even — it does *not* reproduce the original
Python `round()` defect on its own. The seeded defect uses
`BigDecimal(...).setScale(2, RoundingMode.HALF_EVEN)` deliberately, so
the half-to-even behaviour is explicit in the code rather than an
implicit language default. Point this out if anyone assumes Java's
rounding "just behaves like Python's."

---

*Coderrange · corporate training and engineering capability*
