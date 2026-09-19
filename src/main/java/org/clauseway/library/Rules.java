package org.clauseway.library;

// ABOUTME: The derived relations (IDB) over one fact base — one method per rule,
// ABOUTME: bodies in literal style: negation over events, argmin queues, denials.

import static org.clauseway.library.Schema.book;
import static org.clauseway.library.Schema.cancelled;
import static org.clauseway.library.Schema.copy;
import static org.clauseway.library.Schema.fulfilled;
import static org.clauseway.library.Schema.loan;
import static org.clauseway.library.Schema.member;
import static org.clauseway.library.Schema.reservation;
import static org.clauseway.library.Schema.returned;
import static org.clauseway.library.Schema.tier;
import static org.clauseway.logic.goals.Goal.defer;
import static org.clauseway.logic.nogoods.Exclusion.exclude;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.clauseway.pldb.relations.Projected.projected;

import org.clauseway.logic.aggregate.Aggregate;
import org.clauseway.logic.finitedomain.Dates;
import org.clauseway.logic.finitedomain.Ints;
import org.clauseway.logic.unification.Unifiable;
import org.clauseway.pldb.AnswerSource;
import java.time.LocalDate;
import org.clauseway.pldb.relations.Literal;

final class Rules {

	private final AnswerSource db;

	Rules(AnswerSource db) {
		this.db = db;
	}

	/** loan without a return event. */
	Literal activeLoan(Unifiable<Integer> loanId, Unifiable<Integer> copyId,
			Unifiable<Integer> memberId, Unifiable<LocalDate> dueDay) {
		return Literal.relation(Rules.class, "activeLoan")
				.arg("loanId", loanId)
				.arg("copyId", copyId)
				.arg("memberId", memberId)
				.arg("dueDay", dueDay)
				.solving(loan(db, loanId, copyId, memberId, dueDay)
						.and(exclude(returned(db, loanId))));
	}

	/** loan WITH its return event — the spent cluster compaction may shed. */
	Literal closedLoan(Unifiable<Integer> loanId, Unifiable<Integer> copyId,
			Unifiable<Integer> memberId, Unifiable<LocalDate> dueDay) {
		return Literal.relation(Rules.class, "closedLoan")
				.arg("loanId", loanId)
				.arg("copyId", copyId)
				.arg("memberId", memberId)
				.arg("dueDay", dueDay)
				.solving(loan(db, loanId, copyId, memberId, dueDay)
						.and(returned(db, loanId)));
	}

	/** copy not on loan: ¬∃ of activeLoan onto the copy, stated inline. */
	Literal availableCopy(Unifiable<Integer> copyId, Unifiable<String> isbn) {
		return Literal.relation(Rules.class, "availableCopy")
				.arg("copyId", copyId)
				.arg("isbn", isbn)
				.solving(copy(db, copyId, isbn)
						.and(exclude(activeLoan(projected(), copyId, projected(), projected()))));
	}

	/** active loan whose due day lies strictly before the given day. */
	Literal overdue(Unifiable<Integer> loanId, Unifiable<LocalDate> today) {
		Unifiable<LocalDate> d = lvar();
		return Literal.relation(Rules.class, "overdue")
				.arg("loanId", loanId)
				.arg("day", today)
				.solving(activeLoan(loanId, lvar(), lvar(), d).and(Dates.lss(d, today)));
	}

	/** The member's lending policy, read through their tier. */
	Literal loanPolicy(Unifiable<Integer> memberId, Unifiable<Integer> loanLimit,
			Unifiable<Long> loanDays) {
		Unifiable<String> t = lvar();
		return Literal.relation(Rules.class, "loanPolicy")
				.arg("memberId", memberId)
				.arg("loanLimit", loanLimit)
				.arg("loanDays", loanDays)
				.solving(member(db, memberId, lvar(), t)
						.and(tier(db, t, loanLimit, loanDays)));
	}

	/**
	 * The loan's due day: the checkout day plus the member's tier length —
	 * a relation over the triple, so any position falls out of the other
	 * two: the due day from the checkout, or the checkout from the due day.
	 */
	Literal dueDate(Unifiable<Integer> memberId, Unifiable<LocalDate> day,
			Unifiable<LocalDate> dueDay) {
		Unifiable<Long> len = lvar();
		return Literal.relation(Rules.class, "dueDate")
				.arg("memberId", memberId)
				.arg("day", day)
				.arg("dueDay", dueDay)
				.solving(loanPolicy(memberId, lvar(), len)
						.and(Dates.addo(day, len, dueDay)));
	}

	/** reservation without a cancellation or fulfillment event. */
	Literal liveReservation(Unifiable<Integer> resId, Unifiable<String> isbn,
			Unifiable<Integer> memberId, Unifiable<LocalDate> day) {
		return Literal.relation(Rules.class, "liveReservation")
				.arg("resId", resId)
				.arg("isbn", isbn)
				.arg("memberId", memberId)
				.arg("day", day)
				.solving(reservation(db, resId, isbn, memberId, day)
						.and(exclude(cancelled(db, resId)))
						.and(exclude(fulfilled(db, resId))));
	}

	/**
	 * The member holding the title's oldest live reservation — ids are issued
	 * in order, so min id is FIFO. Mode-restricted: the aggregate inside
	 * needs the isbn ground at the probe.
	 */
	Literal queueHead(Unifiable<String> isbn, Unifiable<Integer> memberId) {
		return Literal.relation(Rules.class, "queueHead")
				.arg("isbn", isbn)
				.arg("memberId", memberId)
				.solving(defer(() -> {
					Unifiable<Integer> minR = lvar();
					return Aggregate.min(r -> liveReservation(r, isbn, lvar(), lvar()), minR)
							.and(liveReservation(minR, isbn, memberId, lvar()));
				}));
	}

	/**
	 * The checkout policy as its own complement: one disjunct per violated
	 * rule, each unifying the reason with its name. Permission is defined as
	 * the ABSENCE of denials — there is no positive twin to drift from.
	 * Mode-restricted: the loan-limit count needs member and copy ground.
	 */
	Literal checkOutDenial(Unifiable<Integer> memberId, Unifiable<Integer> copyId,
			Unifiable<String> reason) {
		return Literal.relation(Rules.class, "checkOutDenial")
				.arg("memberId", memberId)
				.arg("copyId", copyId)
				.arg("reason", reason)
				.solving(exclude(member(db, memberId, projected(), projected()))
						.and(reason.unifies("not a member"))
						.or(exclude(copy(db, copyId, projected()))
								.and(reason.unifies("no such copy")))
						.or(member(db, memberId, projected(), projected())
								.and(exclude(loanPolicy(memberId, projected(), projected())))
								.and(reason.unifies("no loan policy")))
						.or(defer(() -> {
							Unifiable<String> i = lvar();
							return copy(db, copyId, i)
									.and(exclude(availableCopy(copyId, i)))
									.and(reason.unifies("copy not available"));
						}))
						.or(defer(() -> {
							Unifiable<Integer> n = lvar();
							Unifiable<Integer> limit = lvar();
							return loanPolicy(memberId, limit, lvar())
									.and(Aggregate.<Integer> count(
											l -> activeLoan(l, lvar(), memberId, lvar()), n))
									.and(Ints.geq(n, limit))
									.and(reason.unifies("at loan limit"));
						}))
						.or(defer(() -> {
							Unifiable<String> i = lvar();
							Unifiable<Integer> h = lvar();
							return copy(db, copyId, i)
									.and(queueHead(i, h))
									.and(exclude(h.unifies(memberId)))
									.and(reason.unifies("title held for another member"));
						})));
	}

	/** The reserve policy's complement, same shape as checkOutDenial. */
	Literal reserveDenial(Unifiable<Integer> memberId, Unifiable<String> isbn,
			Unifiable<String> reason) {
		return Literal.relation(Rules.class, "reserveDenial")
				.arg("memberId", memberId)
				.arg("isbn", isbn)
				.arg("reason", reason)
				.solving(exclude(member(db, memberId, projected(), projected()))
						.and(reason.unifies("not a member"))
						.or(exclude(book(db, isbn, projected(), projected()))
								.and(reason.unifies("no such title")))
						.or(liveReservation(lvar(), isbn, memberId, lvar())
								.and(reason.unifies("already holds a reservation"))));
	}
}
