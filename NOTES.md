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

## 7. Entry 4 dissolved: reservations became events

The delete friction was self-inflicted — the model had two write disciplines:
loans were events (append-only, the present derived by negation) while
reservations were destructively updated. Making cancellation and fulfillment
events (cancelled/fulfilled relations, liveReservation = reservation ∧
¬cancelled ∧ ¬fulfilled) removed every delete from the domain: all four
commands are now validate-then-append, Database.withoutFacts has no caller
here, and the Postgres shape improves to INSERT-only event tables. The
lesson generalizes: where a relational domain layer seems to need deletion,
look for the event the deletion was impersonating.

## 8. Positional arguments hide the schema's names (Tom, Sep 2026)

Every exists/posted call is positional Unifiables; the argument names live in
Property objects the call site can't see, so reading or writing a lookup
means re-opening the relation definition. Java can't fix this positionally
(relations are runtime values — parameter names are v0/v1 forever). The
design that fits: binding objects on Property — copy.exists(db, copyId.is(c),
isbn.is("978-0")) — order-free, per-binding typed, arity checked at runtime.
Convergence: an OMITTED property = fresh local = ∃-projection in a positive
seat, so named args and entry 1's projection door are one feature. The trap:
omission under exclude() is precisely the free-var quantifier bug — the
negated seat must refuse omission (projecting() through a real Derived is
the sanctioned spelling). Names awaiting ratification: is / projecting.

## 9. SQL joins: the gap is seam vocabulary, not literal ergonomics

Worry (Tom): auto-exists literals might foreclose SQL join pushdown. They
don't — goal combinators are immutable values (#103), a positive literal is
a LookupGoal (relation, source, args), so a same-source conjunction with
shared variables is inspectable, join-recognizable syntax; negated postings
render as NOT EXISTS (liveReservation's body = one SELECT, two anti-joins).
The REAL blockers: (a) Call is single-relation — the seam needs a
conjunctive-query question shape plus a source capability, the same
extension family as entry 6's FoldSpec; (b) table boundaries — joins across
Deriveds stay engine-side by construction (materialization), but the
practical joins live INSIDE bodies where a fusion pass reaches them. Risk
profile: fusion is purely additive; the per-literal fallback is always
correct, so the planner can refuse when unsure. Side finding: defer()
thunks are the actual syntax-destroyers — block-bodied lambdas scope locals
without hiding literals from a future planner.

## 10. Join execution: fusion is a store-family pass, not a goal rewrite

Tom's worry: postings probe eagerly on wake; join pushdown needs delayed
fetches + shared-variable recognition, which looks like optimizer surgery
(push lookups to conjunction end, insert a re-evaluating wakeup goal). The
reframe: the store already holds the joint-query node — the family's records
ARE delayed fetches in registration form, wakes already re-walk args, and
"the end of the conjunction" is enforcement. Join variables are
substitution-dependent, so grouping must happen at fetch time on walked
terms — which the family-level view does by construction; static analysis
under-detects (aliasing grows monotonically). Ladder: tier 1 = lazy
postings (imposition registers, fetch on trigger: threshold/labelling/
policy — store-local); tier 2 = family-fused fetch (group parked records by
source + walked alias classes → conjunctive Call → one JOIN). Optimizer's
role shrinks to ordering + fetch-policy pricing. Tradeoff: eager probes buy
propagation (supports, doomed) — laziness is a priced profile, not a
default; estimate keeps doomed cheap either way. Anti-join fusion (NOT
EXISTS) deferred: negated literals live in the nogood store, cross-family.
Candidate for graduation to a logic/docs/notes one-idea note.

## 11. Mint-new-per-write is snapshot isolation in disguise

Tom's question: Library mints a new Library+Rules per write — would that
work against Postgres? Yes, but only because the pattern silently assumes
what ImmutableDatabase provides: a source that is a VALUE. Fresh Rules per
write = every memoizing layer (owned tables, coverage ledger) lives exactly
as long as the state it memoizes — cache invalidation by reconstruction,
sound by construction. Against a connection (shared mutable world) the
naive port breaks twice: shared caches outliving a write hold coverage
proofs about the old state (§5.1 stability, the unchecked isolation()
witness), and autocommit + concurrent writers tear reads WITHIN one solve
(liveReservation's two probes straddling a foreign commit — a failure the
in-memory version cannot even express). The port: Library value ↔ one
REPEATABLE READ transaction; MVCC snapshot plays ImmutableDatabase;
commands are denials-then-INSERT in the same transaction; commit is the
write face; next Library = next transaction. Per-value caches stay sound
and useful (ORM first-level-cache cost profile). Cross-transaction reuse =
the pins design (txid as the Pin), deferred on its triggers.

## 12. Tables assume one solve; the produce CAS is not a concurrency story

Tom's observation: table() extraction + REST means two solves in two
threads can run on one table — never openly considered. The partition:
SEALED entries are immutable values, cross-solve replay is sound (the
warm-start story). OPEN entries embed three single-solve assumptions the
plant-once CAS does not cover: (a) cross-scheduler wakes — a foreign
reader's parked frame must become runnable on ITS scheduler (#64's ready
door, still pending, is the missing primitive); (b) the abandoned master —
pull-based solve streams can walk away holding the claim, leaving a
poisoned key (claimed, unsealed, undeliverable) with no lease/abandonment
story; (c) completion accounting — group seal and region billing are
ambient per-solve, foreign readers corrupt the arithmetic. The library is
immune TODAY because entry 11's mint-new pattern gives fresh tables per
request — concurrency isolation as the third job of reconstruction. The
shared-table scenario becomes reachable exactly when cross-request reuse
(pins/warm start) is built: one deferred decision, not two. Near-term
hardening candidate (STOP-listed, needs the go): refuse loudly when a
reader from a different drive joins an open entry.

## 13. Pins don't simplify the PG port — but event sourcing made them cheap

Tom's question: would pins simplify the Postgres integration? No: the
transaction-scoped port (entry 11) is simple BECAUSE nothing outlives its
snapshot — pins defend claims that outlive theirs, so they only add code
there. Their cheapest tier (record source tokens per solve, refuse loudly
on conflict, no reuse policies) buys hardening — turning one-snapshot-per-
Library from convention into checked invariant — not simplicity. The
finding worth keeping: entry 7's INSERT-only schema gives every relation a
natural pin, its high-water mark (one indexed SELECT max() to check). But
reuse must be iff-EQUAL, not monotone: derived relations negate, so an
append can kill view rows — the log grows monotonically, the views don't.
Exactly why Pin separates per-class reuse policy from leq. When the
cross-request-reuse trigger fires, this domain is the easy case.

## 14. Solve-scoped tables: return to package residence (Tom's proposal)

Instead of pins: make Derived tables solve-scoped. This is not a workaround
— it returns to the engine's own doctrine (the compression's residence is a
PACKAGE); TabledSource owning its table was the deviation, motivated by
warm-start, which was then shelved — capability without a consumer,
carrying entries 11/12 as liabilities. Mechanism: NOT clear-on-complete
(close contract is hazy; clearing a shared Derived under a concurrent
solve re-imports the race) but fresh-per-solve — a table registry planted
at the solve root package, Deriveds resolve their table from the consuming
package (keyed by Derived identity; within-solve memoization untouched;
produce still re-bases for substitutions, carrying the registry). What
dissolves: entry 12 wholesale, the mint-new pattern's concurrency job
(static shared Rules becomes safe), pins shrink to source caches (caller's
transaction problem). Cost: inter-solve replay (shipped + receipted) is
torn down; warm start returns as an EXPLICIT seeding door — the caller
threads a table forward when it knows the world stood still ("engine
computes, caller persists" applied to caches). Design pass questions:
registry planted at solve root (sibling branches must not mint
duplicates); what table() becomes; seeded-solve receipts replacing the
replay receipts. STOP-listed arc; awaits the go.

## 15. Source caches are snapshot-scoped, and entry 14 over-claimed

Tom extended entry 14 to CachingAnswerSource: shouldn't it be per-solve
too? The taxonomy that answers it: a memo's lifetime must equal the
lifetime of the world it memoizes. Tables memoize the derivation universe
— per-solve, package residence. Source pools memoize the SOURCE SNAPSHOT —
per-transaction; per-solve would be sound but needlessly narrow (the
pool's payoff is cross-solve reuse within one request, sound under one
REPEATABLE READ snapshot). The library's pattern already scopes it right;
missing is enforcement (isolation() witness unchecked; the wrapper is
single-threaded by assumption). The correction to entry 14: solve-scoped
tables do NOT make static Rules safe — Derived bodies CAPTURE the source,
and a static Rules freezes one db (and its cache) past its snapshot; the
source capture is the residual world-coupling. Design fork for the
residence arc: (1) Rules stays per-world (per-Library) — least machinery,
static Rules not a goal; (2) Rules world-free — sources arrive through the
solve-root registry like tables, seeding threads both memo kinds, exists()
loses its db argument to late binding. Lean: (1), with (2) triggered only
by the REST endpoint-generator use case reviving.

## Positive receipt: argmin is one goal, not a gap

The reservation-queue head (member holding the minimum reservation id) looked
like it would need two solves; it's one goal — Aggregate.min binds the id and
an ordinary join reads the member off it. No friction, recording the pattern
so it isn't re-derived.

## Positive receipt: count over a tabled derived relation

Aggregate.count over activeLoan (a Derived, hence tabled production behind a
LookupGoal) folds correctly — the historical "findall over a cold tabled goal
is empty" probe did not reproduce. The borrow-limit policy stands on it.
