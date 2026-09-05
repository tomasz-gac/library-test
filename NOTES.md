# Friction ledger

The point of this project: model a library's domain layer relationally on the
engine and record every place the engine can't express something, forces a
workaround, or wants a constraint store that doesn't exist. One entry per
friction, appended as hit, never rewritten — this file is evidence, not a doc.

Entry format: what I wanted to say, what I had to say instead, what the engine
would need.

## 1. ¬∃ needs a named projection relation

Wanted: "copy is available when NO active loan exists for it" — a negated
existential, ¬∃l,m,d activeLoan(l,c,m,d), written inline. Had to: name the
projection as its own derived relation (onLoan(c)) and negate that, because
exclude() takes whole-row postings — free variables in a negated literal read
as the wide row (∀-flavored), not ∃. This is Datalog-standard (project before
negating), so arguably discipline rather than gap — but an anonymous
∃-projection door on Derived (`derived.projected(keep...)`) would erase the
boilerplate relation. Engine cost: it's just solving() over an exists() with
local lvars — pure sugar, no new machinery.

## 2. Days are ints because there is no ordered domain over dates

Wanted: dueDay/day as LocalDate with `before(due, today)`. Had to: encode days
as Integer (epoch-day convention) so FiniteDomain.lss applies. Works, but the
domain type is a lie the schema tells — nothing stops a caller from comparing
a day to a copyId. What the engine would need: the ordered-domain store over
any Comparable (the Range/order family from the scrapped constraint-kit plan —
this is its first real pull receipt).

## 3. Command validation is queries-plus-ifs, not a relational rule

Wanted: "may check out" as one derived relation the command consults, with the
denial REASON as part of the answer. Had to: three separate boolean solves
(member? available? under limit?) glued with Java ifs, because a relational
query answers "which tuples hold", not "why does none hold". The engine's
negative space has no witness. This is arguably essential (a failed solve
carries no derivation), but a domain layer wants diagnoses; the workaround is
the command layer owning the reasons. Related door: conditional answers
already carry "holds IF" — a maybeCheckOut relation with residues would say
"yes if you return something first". Not built; noted.

## 4. Deleting a fact means reading it back whole first

Wanted: cancelReservation(resId) — remove by key. Had to: solve for the full
tuple (isbn, member, day), rebuild the complete Fact, then withoutFacts — the
Database removes facts by value, not by key, so every targeted delete is a
query-then-remove round trip. Reasonable for a value store, but the domain
layer grows a "read the whole row to forget it" idiom. A withoutMatching
(relation + bound pattern) door on Database would collapse it.

## 5. Entry 3's answer: invert the face — policy as its own complement

Tom's move: don't ask "may check out" (a conjunction with no witness when it
fails); define denial(m, c, reason) — one disjunct per violated rule, each
unifying reason with its name. De Morgan at the modelling level: the negated
conjunction becomes a disjunction of positive, nameable violations. Permission
= no denials, so there is no positive twin to drift from; violations enumerate
instead of first-failing; the command is one solve plus a fact append. Costs:
(a) MODE RESTRICTION — the loan-limit disjunct counts, and the count's inputs
must be ground, so denial only answers ground (m, c) probes; the first
relation here that isn't fully relational in its arguments. (b) Reasons name
the failed guard, they don't explain it — structural reasons (heldBy(m'))
are the upgrade if wanted. Exercises: negation three deep
(¬availableCopy → ¬onLoan → ¬returned), aggregate + FD geq inside a tabled
disjunct, a derived (queueHead, itself argmin-shaped) consumed under
disequality. All worked without engine friction.

## 6. Aggregation does not push down (Tom's observation, Sep 2026)

Migrating this domain to Postgres, every Aggregate is fetch-all-then-fold:
the sub-goal is an opaque closure, and pushdown needs syntax (the
Theory-as-syntax lesson — the SQL compiler walks atoms, not goals). The
loan-limit count would stream the member's loans over the wire to count
them; the DB wanted one COUNT(*) round trip. Three tiers of answer:
(a) NOW, no engine change — aggregation-bearing rules migrate as SQL views
exposed as base relations; fold placement becomes a per-relation sourcing
decision, priced by view/rule drift. (b) DESIGNABLE, small — a fold
capability on the source seam, generalizing estimate(Call) (which is
already COUNT pushed down, priced): fold(Call, FoldSpec) with FoldSpec a
closed vocabulary (count/sum/min/max over a column), consulted by Aggregate
only when the sub-goal normalizes to a single Call on a fold-capable
source; queueHead's min is exactly this shape, the loan-limit count is not.
(c) SHELVED — folds over derived sub-plans (the loan-limit count) need the
goals-as-data fold-planner; stays shelved.

## Positive receipt: argmin is one goal, not a gap

The reservation-queue head (member holding the minimum reservation id) looked
like it would need two solves; it's one goal — Aggregate.min binds the id and
an ordinary join reads the member off it. No friction, recording the pattern
so it isn't re-derived.

## Positive receipt: count over a tabled derived relation

Aggregate.count over activeLoan (a Derived, hence tabled production behind a
LookupGoal) folds correctly — the historical "findall over a cold tabled goal
is empty" probe did not reproduce. The borrow-limit policy stands on it.
