# Simple Prompts — Create cartsvc, CLAUDE.md, AGENTS.md, Sub-agent (Java stack)

Run prompt 0 once to build the repository. Run prompts 1-3 inside the
`cartsvc-java/` folder afterward.

---

## 0. Create the cartsvc project

Claude Code, Docker available. Not chat.

```
Build cartsvc-java, a small Java teaching repository for a cart and
checkout service. This is a training artefact, not a product.

STACK
Java 17, Maven, JUnit 5, JDBC (a plain DataSource — no ORM, keep the
raw-SQL surface visible for the lab). PostgreSQL. No other dependencies.

DOMAIN
Cart and checkout for a storefront. A checkout validates stock for
every line, applies a coupon, scores the order for fraud risk, commits
stock, and writes the order.

LAYOUT
Six classes in src/main/java/com/shop/cart/, each with a single
responsibility:
  - CartService.java   — add items, checkout
  - CouponService.java — validate() and redeem()
  - PricingService.java — totals(), tax, discount
  - CatalogService.java — search()
  - RiskService.java   — isBlocked()
  - Db.java            — connection handling, schema

SEEDED DEFECTS — do not tell participants
  1. Stock checked in CartService, then decremented later — not atomic.
     The check loop and the UPDATE must be separated by coupon
     validation, pricing and the risk call, so the window is wide, not
     a one-line race.
  2. Coupon usage limit checked, then incremented later — not atomic.
     CouponService.validate() and redeem() are called at different
     points in CartService, not inside one transaction.
  3. GST computed on the pre-discount subtotal. PricingService.totals()
     calls taxOn(subtotal) before the discount is subtracted. Every
     pricing method must be individually correct and individually
     tested — only the composition is wrong.
  4. CatalogService.search() builds its SQL with string
     concatenation/interpolation directly from the query parameter —
     SQL injection.
  5. CartService's final log line at the end of checkout writes the
     customer's email and full address to the application log.
  6. CartService.addItem() stores unitPrice at add-to-cart time;
     checkout() never rechecks it against current price.

DESIGN RULES
- 20 unit/integration tests must PASS while all six defects are live.
  None of them may assert an end-to-end checkout total, and none may
  run anything concurrently — that gap is what the lab is about.
- Build a separate, non-test fixture — a small runnable class,
  ConcurrentCheckoutFixture, under
  src/main/java/com/shop/cart/fixtures/ — that fires concurrent
  checkouts against the same low-stock product and the same
  single-use coupon and FAILS both scenarios (oversell, over-redeem)
  when run. This fixture is NOT wired into `mvn test`.
- No comment anywhere hints that code is intentionally wrong. No TODO,
  no "note: race here". The code must read as ordinary work under
  deadline.
- Realistic mess is fine: inconsistent naming, one thing left half
  cleaned up. Undocumented does not mean clean.
- No foreign keys or unique constraints in the schema.

ACCEPTANCE — do not present anything until all hold
- mvn test passes, 20 tests, all six defects live
- running the ConcurrentCheckoutFixture main class fails both
  scenarios on 3 consecutive runs (paste all three outputs) —
  typically 4-6 orders written against 3 units of stock, and the
  single-use coupon redeemed more than once
- calling PricingService.totals() directly with a ₹1,000 cart and a
  20% coupon demonstrably taxes ₹36 more than it should (paste the
  numbers)
- you paste the actual output of every command above, not a summary

Iterate until acceptance is met. Do not ask me between attempts.
Report at the end: what failed on the way, and what you changed.
```

---

## 1. CLAUDE.md

```
Look at this repository and write a CLAUDE.md file.

Include only:
- what each class in src/main/java/com/shop/cart/ is responsible for,
  one line each
- the real verified test command (run it first, don't guess)
- one workflow rule: PricingService.java needs a test proving current
  behaviour before any change, because it has a bug that passes
  all existing tests silently
- one rule about what must never happen (no string-built SQL,
  no float/double in money calculations)

Keep it under 40 lines. Don't explain, just state the rules.
```

**What this produces:** a short file that loads automatically every
time a Claude session opens in this repo — so nobody has to be told
about the pricing bug twice.

---

## 2. AGENTS.md

```
This repo might be opened by different AI coding tools, not just you.

Write an AGENTS.md with only the facts that would be true no matter
which tool is reading it: the real test command, the folder layout,
and the one rule about PricingService.java needing a test first.

Nothing Claude-specific — no report formats, no Claude Code workflow
instructions. Just facts and commands any tool would need.
```

**What this produces:** the same core facts as CLAUDE.md, but written
so a different tool (Codex, or anything else) gets the same
information without you maintaining two versions that could drift
apart.

**Only do this if you're actually using more than one tool on this
repo.** If it's Claude only, skip this file — CLAUDE.md alone is
enough.

---

## 3. Sub-agent instruction

Not a file — this is what you type when you want a one-off, disposable
search. Try it directly:

```
Use a sub-agent for this. Read-only.

Search every file in src/main/java/com/shop/cart/ and tell me: which
methods touch money values (prices, totals, discounts) and use
double/float instead of a safer decimal type (BigDecimal)?

Return only a list: file, line, method name. Nothing else.
```

**What this produces:** the sub-agent opens all six files, does the
search, and hands back a short list. None of the file contents it
read stay in your conversation — only the list does.

---

## The one-line difference between all three

CLAUDE.md and AGENTS.md are things you write once and they stay,
loaded every session. The sub-agent prompt is something you type
fresh each time you want a disposable search — there's no file to
create for it.
