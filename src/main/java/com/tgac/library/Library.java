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

import com.tgac.functional.category.Nothing;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.inmemory.SharedDatabase;
import com.tgac.pldb.relations.Literal;
import com.tgac.pldb.transaction.AbstractTransaction;
import com.tgac.pldb.transaction.Transaction;
import io.vavr.control.Try;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Library implements AutoCloseable {

	private final Transaction db;
	private final Rules rules;

	private Library(Transaction db) {
		this.db = db;
		this.rules = new Rules(db);
	}

	/** A private one-history world — the pure-value mode for tests and demos. */
	public static Library empty() {
		return over(AbstractTransaction.over(SharedDatabase.empty().open("library")));
	}

	public static Library over(Transaction transaction) {
		return new Library(transaction);
	}

	/**
	 * The write face: everything appended by this value's lineage lands,
	 * certified against the reads that justified it; a {@code Conflict}
	 * failure means the world moved — reopen and re-solve.
	 */
	public Try<Nothing> commit() {
		return db.commit();
	}

	/** Ends the transaction; every value of this lineage is spent with it. */
	@Override
	public void close() throws Exception {
		db.close();
	}

	// -- the write side: facts and events --------------------------------

	public Library withBook(String isbn, String title, String author) {
		return with(book(db, lval(isbn), lval(title), lval(author)));
	}

	public Library withCopy(int copyId, String isbn) {
		return with(copy(db, lval(copyId), lval(isbn)));
	}

	public Library withMember(int memberId, String name, String tier) {
		return with(member(db, lval(memberId), lval(name), lval(tier)));
	}

	public Library withTier(String name, int loanLimit, int loanDays) {
		return with(Schema.tier(db, lval(name), lval(loanLimit), lval(loanDays)));
	}

	private Library with(Literal row) {
		return new Library(db.withFacts(row).get());
	}


	// -- commands --------------------------------------------------------

	public Try<Library> checkOut(int loanId, int copyId, int memberId, int day) {
		List<String> denials = checkOutDenials(memberId, copyId);
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(with(loan(db, lval(loanId), lval(copyId), lval(memberId),
				lval(dueDayOf(memberId, day))))
				.fulfillReservationOf(memberId, isbnOf(copyId)));
	}

	/** The due day the member's tier grants from the checkout day. */
	private int dueDayOf(int memberId, int day) {
		Unifiable<Integer> due = lvar();
		return rules.dueDate(lval(memberId), lval(day), due).solve(due)
				.findFirst().map(Reified::get)
				.orElseThrow(() -> new IllegalStateException("no loan policy: " + memberId));
	}

	public Try<Library> reserve(int resId, String isbn, int memberId, int day) {
		List<String> denials = reserveDenials(memberId, isbn);
		if (!denials.isEmpty()) {
			return Try.failure(new IllegalStateException(String.join("; ", denials)));
		}
		return Try.success(with(reservation(db, lval(resId), lval(isbn), lval(memberId), lval(day))));
	}

	public Try<Library> cancelReservation(int resId) {
		if (!hasLiveReservation(resId)) {
			return Try.failure(new IllegalStateException("no reservation: " + resId));
		}
		return Try.success(with(cancelled(db, lval(resId))));
	}

	public Try<Library> returnCopy(int loanId) {
		if (!hasActiveLoan(loanId)) {
			return Try.failure(new IllegalStateException("no active loan: " + loanId));
		}
		return Try.success(with(returned(db, lval(loanId))));
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
		return rules.liveReservation(r, lval(isbn), lval(memberId), d)
				.solve(r)
				.findFirst().map(Reified::get);
	}

	private Library fulfillReservationOf(int memberId, String isbn) {
		return reservationOf(memberId, isbn)
				.map(r -> with(fulfilled(db, lval(r))))
				.orElse(this);
	}

	private boolean hasLiveReservation(int resId) {
		Unifiable<String> i = lvar();
		Unifiable<Integer> m = lvar();
		Unifiable<Integer> d = lvar();
		return rules.liveReservation(lval(resId), i, m, d).solve(i).findAny().isPresent();
	}

	private boolean hasActiveLoan(int loanId) {
		Unifiable<Integer> c = lvar();
		Unifiable<Integer> m = lvar();
		Unifiable<Integer> d = lvar();
		return rules.activeLoan(lval(loanId), c, m, d).solve(c).findAny().isPresent();
	}

	// -- the read side: relational queries -------------------------------

	public List<Integer> copiesOf(String isbn) {
		Unifiable<Integer> c = lvar();
		return values(copy(db, c, lval(isbn)).solve(c));
	}

	public List<Integer> availableCopies(String isbn) {
		Unifiable<Integer> c = lvar();
		return values(rules.availableCopy(c, lval(isbn)).solve(c));
	}

	public List<Integer> activeLoansOf(int memberId) {
		Unifiable<Integer> l = lvar();
		return values(rules.activeLoan(l, lvar(), lval(memberId), lvar()).solve(l));
	}

	public List<Integer> overdueLoans(int today) {
		Unifiable<Integer> l = lvar();
		return values(rules.overdue(l, lval(today)).solve(l));
	}

	public Optional<Integer> nextInQueue(String isbn) {
		Unifiable<Integer> h = lvar();
		return rules.queueHead(lval(isbn), h).solve(h).findFirst().map(Reified::get);
	}

	/** Every rule the checkout would violate, by name; empty means allowed. */
	public List<String> checkOutDenials(int memberId, int copyId) {
		Unifiable<String> r = lvar();
		return values(rules.checkOutDenial(lval(memberId), lval(copyId), r).solve(r));
	}

	/** Every rule the reservation would violate, by name; empty means allowed. */
	public List<String> reserveDenials(int memberId, String isbn) {
		Unifiable<String> r = lvar();
		return values(rules.reserveDenial(lval(memberId), lval(isbn), r).solve(r));
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
