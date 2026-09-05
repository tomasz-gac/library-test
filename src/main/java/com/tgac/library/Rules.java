package com.tgac.library;

// ABOUTME: The derived relations (IDB) over one fact base: active loans by
// ABOUTME: negation over return events, availability by negation over loans.

import static com.tgac.logic.finitedomain.FiniteDomain.geq;
import static com.tgac.logic.finitedomain.FiniteDomain.lss;
import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.aggregate.Aggregate;
import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.inmemory.Database;
import com.tgac.pldb.relations.Property;
import com.tgac.pldb.relations.Relations;

final class Rules {

	private static final Relations._4<Integer, Integer, Integer, Integer> activeLoanRel =
			Relations.relation("activeLoan", Schema.loanId, Schema.copyId, Schema.memberId, Schema.dueDay);
	private static final Relations._1<Integer> onLoanRel =
			Relations.relation("onLoan", Schema.copyId);
	private static final Relations._2<Integer, String> availableCopyRel =
			Relations.relation("availableCopy", Schema.copyId, Schema.isbn);
	private static final Relations._2<Integer, Integer> overdueRel =
			Relations.relation("overdue", Schema.loanId, Schema.day);
	private static final Relations._1<Integer> registeredMemberRel =
			Relations.relation("registeredMember", Schema.memberId);
	private static final Relations._1<Integer> knownCopyRel =
			Relations.relation("knownCopy", Schema.copyId);
	private static final Relations._2<String, Integer> queueHeadRel =
			Relations.relation("queueHead", Schema.isbn, Schema.memberId);
	private static final Property<String> reason = Property.<String>of("reason");
	private static final Relations._3<Integer, Integer, String> denialRel =
			Relations.relation("denial", Schema.memberId, Schema.copyId, reason);

	/** loan without a return event. */
	final Relations._4<Integer, Integer, Integer, Integer>.Derived activeLoan;

	/**
	 * The ∃-projection of activeLoan onto the copy: negation is over WHOLE
	 * rows, so "no active loan for this copy" needs the projection named as
	 * its own relation before it can be denied.
	 */
	final Relations._1<Integer>.Derived onLoan;

	/** copy not on loan. */
	final Relations._2<Integer, String>.Derived availableCopy;

	/** active loan whose due day lies strictly before the given day. */
	final Relations._2<Integer, Integer>.Derived overdue;

	/** ∃-projection of member onto the id. */
	final Relations._1<Integer>.Derived registeredMember;

	/** ∃-projection of copy onto the id. */
	final Relations._1<Integer>.Derived knownCopy;

	/**
	 * The member holding the title's oldest reservation — ids are issued in
	 * order, so min id is FIFO. Mode-restricted: the aggregate inside needs
	 * the isbn ground at the probe.
	 */
	final Relations._2<String, Integer>.Derived queueHead;

	/**
	 * The checkout policy as its own complement: one disjunct per violated
	 * rule, each unifying the reason with its name. Permission is defined as
	 * the ABSENCE of denials — there is no positive twin to drift from.
	 * Mode-restricted: the loan-limit count needs member and copy ground.
	 */
	final Relations._3<Integer, Integer, String>.Derived denial;

	Rules(Database db) {
		activeLoan = activeLoanRel.solving((l, c, m, d) ->
				Schema.loan.exists(db, l, c, m, d)
						.and(exclude(Schema.returned.posted(db, l))));
		onLoan = onLoanRel.solving(c -> defer(() -> {
			Unifiable<Integer> l = lvar();
			Unifiable<Integer> m = lvar();
			Unifiable<Integer> d = lvar();
			return activeLoan.exists(l, c, m, d);
		}));
		availableCopy = availableCopyRel.solving((c, i) ->
				Schema.copy.exists(db, c, i)
						.and(exclude(onLoan.posted(c))));
		overdue = overdueRel.solving((l, t) -> defer(() -> {
			Unifiable<Integer> c = lvar();
			Unifiable<Integer> m = lvar();
			Unifiable<Integer> d = lvar();
			return activeLoan.exists(l, c, m, d).and(lss(d, t));
		}));
		registeredMember = registeredMemberRel.solving(m -> defer(() -> {
			Unifiable<String> n = lvar();
			return Schema.member.exists(db, m, n);
		}));
		knownCopy = knownCopyRel.solving(c -> defer(() -> {
			Unifiable<String> i = lvar();
			return Schema.copy.exists(db, c, i);
		}));
		queueHead = queueHeadRel.solving((i, h) -> defer(() -> {
			Unifiable<Integer> minR = lvar();
			return Aggregate.min(r -> defer(() -> {
						Unifiable<Integer> m0 = lvar();
						Unifiable<Integer> d0 = lvar();
						return Schema.reservation.exists(db, r, i, m0, d0);
					}), minR)
					.and(defer(() -> {
						Unifiable<Integer> d = lvar();
						return Schema.reservation.exists(db, minR, i, h, d);
					}));
		}));
		denial = denialRel.solving((m, c, r) ->
				exclude(registeredMember.posted(m))
						.and(r.unifies("not a member"))
						.or(exclude(knownCopy.posted(c))
								.and(r.unifies("no such copy")))
						.or(defer(() -> {
							Unifiable<String> i = lvar();
							return Schema.copy.exists(db, c, i)
									.and(exclude(availableCopy.posted(c, i)))
									.and(r.unifies("copy not available"));
						}))
						.or(defer(() -> {
							Unifiable<Integer> n = lvar();
							return Aggregate.<Integer>count(l -> defer(() -> {
										Unifiable<Integer> c0 = lvar();
										Unifiable<Integer> d0 = lvar();
										return activeLoan.exists(l, c0, m, d0);
									}), n)
									.and(geq(n, lval(Library.LOAN_LIMIT)))
									.and(r.unifies("at loan limit"));
						}))
						.or(defer(() -> {
							Unifiable<String> i = lvar();
							Unifiable<Integer> h = lvar();
							return Schema.copy.exists(db, c, i)
									.and(queueHead.exists(i, h))
									.and(exclude(h.unifies(m)))
									.and(r.unifies("title held for another member"));
						})));
	}
}
