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

## Positive receipt: count over a tabled derived relation

Aggregate.count over activeLoan (a Derived, hence tabled production behind a
LookupGoal) folds correctly — the historical "findall over a cold tabled goal
is empty" probe did not reproduce. The borrow-limit policy stands on it.
