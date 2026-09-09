# library

A lending library's domain layer written entirely on the
[`logic`](../../logic) engine through [`pldb`](../../pldb) — books,
copies, members, tiers, loans, reservations. No framework, no ORM, no
integrations: the domain IS the relations.

This is a proving ground, not a product. Its charter: model a real-ish
domain relationally, and whenever the engine can't express something,
forces a workaround, or wants a store that doesn't exist — write it
down. **`NOTES.md` is that ledger**, and it drives engine design: the
denial-relation inversion, the event-sourcing turn, the relations-as-
functions surface, the ordered-domain pull receipts, and the
transactional write face all started as entries here.

The shape of the code:

- `Schema.java` — one method per base relation; the signature is the
  arity, the builder the columns, the last argument the backing.
- `Rules.java` — one method per derived relation. Negation over events
  (`activeLoan` = loan without a return), argmin queues (`queueHead`),
  and policy as its own complement: `checkOutDenial` enumerates every
  violated rule by name, so permission is the ABSENCE of denials and
  there is no positive twin to drift from.
- `Library.java` — the facade: an immutable value over a
  `Transaction`; commands validate by solving denials, then append
  events; `commit()` lands the lineage's appends, certified against
  the reads that justified them; a `Conflict` means the world moved —
  reopen and re-solve, and the anomaly reappears as an ordinary
  denial.

The tests run the same domain against three worlds: pure in-memory
values, an in-memory shared history (`SharedDatabase`), and real
PostgreSQL under testcontainers — through both serialization kinds.
The domain code cannot tell them apart; that indistinguishability is
the point.
