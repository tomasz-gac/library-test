package com.tgac.library;

// ABOUTME: The derived relations (IDB) over one fact base — one method per rule,
// ABOUTME: bodies in literal style: negation over events, argmin queues, denials.

import static com.tgac.logic.finitedomain.FiniteDomain.geq;
import static com.tgac.logic.finitedomain.FiniteDomain.lss;
import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.nogoods.Exclusion.exclude;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import static com.tgac.library.Schema.book;
import static com.tgac.library.Schema.cancelled;
import static com.tgac.library.Schema.copy;
import static com.tgac.library.Schema.fulfilled;
import static com.tgac.library.Schema.loan;
import static com.tgac.library.Schema.member;
import static com.tgac.library.Schema.reservation;
import static com.tgac.library.Schema.returned;

import com.tgac.logic.aggregate.Aggregate;
import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.inmemory.Database;
import com.tgac.pldb.relations.Literal;

final class Rules {

	private final Database db;

	Rules(Database db) {
		this.db = db;
	}

	/** loan without a return event. */
	Literal activeLoan(Unifiable<Integer> loanId, Unifiable<Integer> copyId,
			Unifiable<Integer> memberId, Unifiable<Integer> dueDay) {
		return Literal.solving("activeLoan",
						loan(db, loanId, copyId, memberId, dueDay)
								.and(exclude(returned(db, loanId))))
				.arg("loanId", loanId)
				.arg("copyId", copyId)
				.arg("memberId", memberId)
				.arg("dueDay", dueDay);
	}

	/**
	 * The ∃-projection of activeLoan onto the copy: negation is over WHOLE
	 * rows, so "no active loan for this copy" needs the projection named as
	 * its own relation before it can be denied.
	 */
	Literal onLoan(Unifiable<Integer> copyId) {
		return Literal.solving("onLoan", activeLoan(lvar(), copyId, lvar(), lvar()))
				.arg("copyId", copyId);
	}

	/** copy not on loan. */
	Literal availableCopy(Unifiable<Integer> copyId, Unifiable<String> isbn) {
		return Literal.solving("availableCopy",
						copy(db, copyId, isbn)
								.and(exclude(onLoan(copyId))))
				.arg("copyId", copyId)
				.arg("isbn", isbn);
	}

	/** active loan whose due day lies strictly before the given day. */
	Literal overdue(Unifiable<Integer> loanId, Unifiable<Integer> today) {
		Unifiable<Integer> d = lvar();
		return Literal.solving("overdue",
						activeLoan(loanId, lvar(), lvar(), d).and(lss(d, today)))
				.arg("loanId", loanId)
				.arg("day", today);
	}

	/** ∃-projection of member onto the id. */
	Literal registeredMember(Unifiable<Integer> memberId) {
		return Literal.solving("registeredMember", member(db, memberId, lvar()))
				.arg("memberId", memberId);
	}

	/** ∃-projection of copy onto the id. */
	Literal knownCopy(Unifiable<Integer> copyId) {
		return Literal.solving("knownCopy", copy(db, copyId, lvar()))
				.arg("copyId", copyId);
	}

	/** ∃-projection of book onto the isbn. */
	Literal knownTitle(Unifiable<String> isbn) {
		return Literal.solving("knownTitle", book(db, isbn, lvar(), lvar()))
				.arg("isbn", isbn);
	}

	/** reservation without a cancellation or fulfillment event. */
	Literal liveReservation(Unifiable<Integer> resId, Unifiable<String> isbn,
			Unifiable<Integer> memberId, Unifiable<Integer> day) {
		return Literal.solving("liveReservation",
						reservation(db, resId, isbn, memberId, day)
								.and(exclude(cancelled(db, resId)))
								.and(exclude(fulfilled(db, resId))))
				.arg("resId", resId)
				.arg("isbn", isbn)
				.arg("memberId", memberId)
				.arg("day", day);
	}

	/**
	 * The member holding the title's oldest live reservation — ids are issued
	 * in order, so min id is FIFO. Mode-restricted: the aggregate inside
	 * needs the isbn ground at the probe.
	 */
	Literal queueHead(Unifiable<String> isbn, Unifiable<Integer> memberId) {
		return Literal.solving("queueHead", defer(() -> {
					Unifiable<Integer> minR = lvar();
					return Aggregate.min(r -> liveReservation(r, isbn, lvar(), lvar()), minR)
							.and(liveReservation(minR, isbn, memberId, lvar()));
				}))
				.arg("isbn", isbn)
				.arg("memberId", memberId);
	}

	/**
	 * The checkout policy as its own complement: one disjunct per violated
	 * rule, each unifying the reason with its name. Permission is defined as
	 * the ABSENCE of denials — there is no positive twin to drift from.
	 * Mode-restricted: the loan-limit count needs member and copy ground.
	 */
	Literal checkOutDenial(Unifiable<Integer> memberId, Unifiable<Integer> copyId,
			Unifiable<String> reason) {
		return Literal.solving("checkOutDenial",
						exclude(registeredMember(memberId))
								.and(reason.unifies("not a member"))
								.or(exclude(knownCopy(copyId))
										.and(reason.unifies("no such copy")))
								.or(defer(() -> {
									Unifiable<String> i = lvar();
									return copy(db, copyId, i)
											.and(exclude(availableCopy(copyId, i)))
											.and(reason.unifies("copy not available"));
								}))
								.or(defer(() -> {
									Unifiable<Integer> n = lvar();
									return Aggregate.<Integer>count(
													l -> activeLoan(l, lvar(), memberId, lvar()), n)
											.and(geq(n, lval(Library.LOAN_LIMIT)))
											.and(reason.unifies("at loan limit"));
								}))
								.or(defer(() -> {
									Unifiable<String> i = lvar();
									Unifiable<Integer> h = lvar();
									return copy(db, copyId, i)
											.and(queueHead(i, h))
											.and(exclude(h.unifies(memberId)))
											.and(reason.unifies("title held for another member"));
								})))
				.arg("memberId", memberId)
				.arg("copyId", copyId)
				.arg("reason", reason);
	}

	/** The reserve policy's complement, same shape as checkOutDenial. */
	Literal reserveDenial(Unifiable<Integer> memberId, Unifiable<String> isbn,
			Unifiable<String> reason) {
		return Literal.solving("reserveDenial",
						exclude(registeredMember(memberId))
								.and(reason.unifies("not a member"))
								.or(exclude(knownTitle(isbn))
										.and(reason.unifies("no such title")))
								.or(liveReservation(lvar(), isbn, memberId, lvar())
										.and(reason.unifies("already holds a reservation"))))
				.arg("memberId", memberId)
				.arg("isbn", isbn)
				.arg("reason", reason);
	}
}
