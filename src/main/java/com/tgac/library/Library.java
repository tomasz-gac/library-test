package com.tgac.library;

// ABOUTME: The library domain facade — an immutable fact base with commands
// ABOUTME: that append events and queries answered relationally by the engine.

import static com.tgac.library.Schema.book;
import static com.tgac.library.Schema.cancelled;
import static com.tgac.library.Schema.copy;
import static com.tgac.library.Schema.fulfilled;
import static com.tgac.library.Schema.loan;
import static com.tgac.library.Schema.member;
import static com.tgac.library.Schema.reservation;
import static com.tgac.library.Schema.returned;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.inmemory.Database;
import com.tgac.pldb.inmemory.ImmutableDatabase;
import com.tgac.pldb.relations.Fact;
import io.vavr.control.Try;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Library {

	public static final int LOAN_LIMIT = 3;

	private final Database db;
	private final Rules rules;

	private Library(Database db) {
		this.db = db;
		this.rules = new Rules(db);
	}

	public static Library empty() {
		return new Library(ImmutableDatabase.empty());
	}

	// -- the write side: facts and events --------------------------------

	public Library withBook(String isbn, String title, String author) {
		return with(book(db, lval(isbn), lval(title), lval(author)).fact());
	}

	public Library withCopy(int copyId, String isbn) {
		return with(copy(db, lval(copyId), lval(isbn)).fact());
	}

	public Library withMember(int memberId, String name) {
		return with(member(db, lval(memberId), lval(name)).fact());
	}

	private Library with(Fact fact) {
		return new Library(db.withFacts(Collections.singletonList(fact)).get());
	}


	// -- commands --------------------------------------------------------

	public Try<Library> checkOut(int loanId, int copyId, int memberId, int dueDay) {
		List<String> denials = checkOutDenials(memberId, copyId);
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(with(loan(db, lval(loanId), lval(copyId), lval(memberId), lval(dueDay)).fact())
				.fulfillReservationOf(memberId, isbnOf(copyId)));
	}

	public Try<Library> reserve(int resId, String isbn, int memberId, int day) {
		List<String> denials = reserveDenials(memberId, isbn);
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(with(reservation(db, lval(resId), lval(isbn), lval(memberId), lval(day)).fact()));
	}

	public Try<Library> cancelReservation(int resId) {
		if (!hasLiveReservation(resId)) {
			return Try.failure(new IllegalStateException("no reservation: " + resId));
		}
		return Try.success(with(cancelled(db, lval(resId)).fact()));
	}

	public Try<Library> returnCopy(int loanId) {
		if (!hasActiveLoan(loanId)) {
			return Try.failure(new IllegalStateException("no active loan: " + loanId));
		}
		return Try.success(with(returned(db, lval(loanId)).fact()));
	}

	private String isbnOf(int copyId) {
		Unifiable<String> i = lvar();
		return copy(db, lval(copyId), i).solve(i)
				.findFirst().map(Reified::get)
				.orElseThrow(() -> new IllegalStateException("no such copy: " + copyId));
	}

	/** The member's live reservation for the title, by id. */
	private Optional<Integer> reservationOf(int memberId, String isbn) {
		Unifiable<Integer> r = lvar();
		Unifiable<Integer> d = lvar();
		return rules.liveReservation.exists(r, lval(isbn), lval(memberId), d)
				.solve(r)
				.findFirst().map(Reified::get);
	}

	private Library fulfillReservationOf(int memberId, String isbn) {
		return reservationOf(memberId, isbn)
				.map(r -> with(fulfilled(db, lval(r)).fact()))
				.orElse(this);
	}

	private boolean hasLiveReservation(int resId) {
		Unifiable<String> i = lvar();
		Unifiable<Integer> m = lvar();
		Unifiable<Integer> d = lvar();
		return rules.liveReservation.exists(lval(resId), i, m, d).solve(i).findAny().isPresent();
	}

	private boolean hasActiveLoan(int loanId) {
		Unifiable<Integer> c = lvar();
		Unifiable<Integer> m = lvar();
		Unifiable<Integer> d = lvar();
		return rules.activeLoan.exists(lval(loanId), c, m, d).solve(c).findAny().isPresent();
	}

	// -- the read side: relational queries -------------------------------

	public List<Integer> copiesOf(String isbn) {
		Unifiable<Integer> c = lvar();
		return values(copy(db, c, lval(isbn)).solve(c));
	}

	public List<Integer> availableCopies(String isbn) {
		Unifiable<Integer> c = lvar();
		return values(rules.availableCopy.exists(c, lval(isbn)).solve(c));
	}

	public List<Integer> overdueLoans(int today) {
		Unifiable<Integer> l = lvar();
		return values(rules.overdue.exists(l, lval(today)).solve(l));
	}

	public Optional<Integer> nextInQueue(String isbn) {
		Unifiable<Integer> h = lvar();
		return rules.queueHead.exists(lval(isbn), h).solve(h).findFirst().map(Reified::get);
	}

	/** Every rule the checkout would violate, by name; empty means allowed. */
	public List<String> checkOutDenials(int memberId, int copyId) {
		Unifiable<String> r = lvar();
		return values(rules.checkOutDenial.exists(lval(memberId), lval(copyId), r).solve(r));
	}

	/** Every rule the reservation would violate, by name; empty means allowed. */
	public List<String> reserveDenials(int memberId, String isbn) {
		Unifiable<String> r = lvar();
		return values(rules.reserveDenial.exists(lval(memberId), lval(isbn), r).solve(r));
	}

	public List<String> titlesBy(String author) {
		Unifiable<String> t = lvar();
		Unifiable<String> i = lvar();
		return values(book(db, i, t, lval(author)).solve(t));
	}

	private static <T> List<T> values(Stream<Reified<T>> answers) {
		return answers.map(Reified::get).collect(Collectors.toList());
	}
}
