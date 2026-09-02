# Per-account accrual rounding — implementation spec (NOT EXECUTED)

**Status: generated only, per explicit instruction not to execute this
prompt.** This is the adapted task template, ready to run in a follow-up
turn. It is written generically because no accrual/rate/reconciliation code
exists anywhere in `seatbook-java` (confirmed by `grep -rliE
"accrual|rate|reconcil|batch" src` — zero matches). The domain objects below
(`Account`, `AccrualService`, a batch job) are placeholders, not real classes
in this repo. Before this can actually be implemented, the real accrual
module — its package, its existing account/rate/schedule types, its
reconciliation job — needs to be pointed to or created from scratch; nothing
here is wired to real file paths.

---

## Constraints (as given, unmodified)

- Decimal only. No `double` or `float` anywhere in the money path.
- `RoundingMode.HALF_UP`-away-from-zero semantics — i.e. Java's
  `RoundingMode.HALF_UP` (Java has no separate "away from zero" mode
  distinct from `HALF_UP`; `HALF_UP` in `java.math` already rounds away
  from zero on a tie, matching .NET's `MidpointRounding.AwayFromZero`).
- Quantize to 2 decimal places: `.setScale(2, RoundingMode.HALF_UP)`.
- Round once, per account. Never round the batch total — the total is the
  sum of already-rounded per-account figures, not a separately rounded
  aggregate.
- Do not change rates, schedule, or the reconciliation job — this is a
  rounding/summation fix scoped to the accrual calculation only.
- Historical balances are out of scope — applies to future accruals only,
  no backfill/migration of existing rows.

---

## 4. IMPLEMENT (to run in a later turn, not this one)

Write the failing test first, in the real accrual test package once
identified (mirroring `src/test/java/com/bus/booking/...` convention used
elsewhere in this repo — JUnit 5, AssertJ if already a dependency, plain
JUnit assertions otherwise, per `SeatBookingServiceTest.java`).

The test must assert **exact** equality (`BigDecimal.equals()` or
`assertEquals` with a `BigDecimal` — not `compareTo` alone, and not any
floating-point delta comparison) between:

```java
BigDecimal sumOfPerAccountAccruals = accounts.stream()
        .map(accrualService::accrueFor)   // rounds once, per account, to 2dp
        .reduce(BigDecimal.ZERO, BigDecimal::add);

BigDecimal batchTotal = accrualService.runBatch(accounts);  // must equal the sum above exactly

assertEquals(sumOfPerAccountAccruals, batchTotal);
```

Run it, confirm it fails (RED) — expected failure mode is a batch total that
diverges from the summed per-account rounding by a few cents, the classic
symptom of rounding the aggregate separately from (or instead of) each
account.

Then write the smallest patch that makes it pass: each account's accrual
computed and rounded to 2dp individually; the batch total defined as the sum
of those already-rounded values, never recomputed by rounding a raw
unrounded aggregate.

## 5. VALIDATE (to run in a later turn, not this one)

- Run the full suite, paste actual console output (not a paraphrase).
- Run accrual over a 5,000-account fixture (needs to be generated or
  located — none exists in this repo currently) and report the batch
  delta: `expectedTotal.subtract(actualBatchTotal)`. Must print exactly
  `0.00`, not "approximately zero" or a rounded display of a nonzero value.

## 6. REVIEW (to run in a later turn, not this one)

Self-review the diff against constraints above; call out anything assumed
rather than verified (e.g. rate source untouched, reconciliation job
untouched, no accidental `double` in a converted field, historical rows
provably unaffected).

## 7. REPORT (to run in a later turn, not this one)

Commands run · files changed · what remains unverified — following the same
format used in `AI_LOG.md` for this repo's other turns.

---

## What's needed before step 4 can start

1. The actual location of the accrual/account/rate code (package + class
   names) — it isn't in this repo as it stands.
2. Confirmation that "batch total" refers to a value already computed
   somewhere (i.e. this is a bug fix on existing logic) versus new logic to
   be written from scratch — constraint 4 ("do not change rates, schedule,
   or the reconciliation job") reads like a bug-fix scope, implying
   pre-existing code I haven't been shown.
3. Whether this belongs in `seatbook-java` at all, or in a different
   project/repo.
