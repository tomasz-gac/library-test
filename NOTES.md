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
