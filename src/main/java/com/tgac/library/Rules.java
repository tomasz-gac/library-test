package com.tgac.library;

// ABOUTME: The derived relations (IDB) over one fact base: active loans by
// ABOUTME: negation over return events, availability by negation over loans.

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.inmemory.Database;
import com.tgac.pldb.relations.Relations;

final class Rules {

	private static final Relations._4<Integer, Integer, Integer, Integer> activeLoanRel =
			Relations.relation("activeLoan", Schema.loanId, Schema.copyId, Schema.memberId, Schema.dueDay);
	private static final Relations._1<Integer> onLoanRel =
			Relations.relation("onLoan", Schema.copyId);
	private static final Relations._2<Integer, String> availableCopyRel =
			Relations.relation("availableCopy", Schema.copyId, Schema.isbn);

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
	}
}
