package com.tgac.library;

// ABOUTME: The library domain facade — an immutable fact base with commands
// ABOUTME: that append events and queries answered relationally by the engine.

import static com.tgac.logic.goals.Goal.defer;
import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.logic.aggregate.Aggregate;
import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Term;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Tuple;
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
		return with(Schema.book.fact(isbn, title, author));
	}

	public Library withCopy(int copyId, String isbn) {
		return with(Schema.copy.fact(copyId, isbn));
	}

	public Library withMember(int memberId, String name) {
		return with(Schema.member.fact(memberId, name));
	}

	private Library with(Fact fact) {
		return new Library(db.withFacts(Collections.singletonList(fact)).get());
	}

	private Library without(Fact fact) {
		return new Library(db.withoutFacts(Collections.singletonList(fact)).get());
	}

	// -- commands --------------------------------------------------------

	public Try<Library> checkOut(int loanId, int copyId, int memberId, int dueDay) {
		if (!isMember(memberId)) {
			return Try.failure(new IllegalStateException("not a member: " + memberId));
		}
		if (!isAvailable(copyId)) {
			return Try.failure(new IllegalStateException("copy not available: " + copyId));
		}
		if (activeLoanCount(memberId) >= LOAN_LIMIT) {
			return Try.failure(new IllegalStateException("member at loan limit: " + memberId));
		}
		String isbn = isbnOf(copyId);
		Optional<Integer> head = nextInQueue(isbn);
		if (head.filter(h -> h != memberId).isPresent()) {
			return Try.failure(new IllegalStateException("title held for member " + head.get()));
		}
		Library lent = with(Schema.loan.fact(loanId, copyId, memberId, dueDay));
		return Try.success(head.isPresent() ? lent.withoutReservationOf(memberId, isbn) : lent);
	}

	public Try<Library> reserve(int resId, String isbn, int memberId, int day) {
		if (!isMember(memberId)) {
			return Try.failure(new IllegalStateException("not a member: " + memberId));
		}
		if (!hasBook(isbn)) {
			return Try.failure(new IllegalStateException("no such title: " + isbn));
		}
		if (reservationOf(memberId, isbn).isPresent()) {
			return Try.failure(new IllegalStateException(
					"member " + memberId + " already holds a reservation for " + isbn));
		}
		return Try.success(with(Schema.reservation.fact(resId, isbn, memberId, day)));
	}

	public Try<Library> cancelReservation(int resId) {
		Unifiable<String> i = lvar();
		Unifiable<Integer> m = lvar();
		Unifiable<Integer> d = lvar();
		return Schema.reservation.exists(db, lval(resId), i, m, d)
				.solve(lval(Tuple.of(i, m, d)))
				.map(Term::get)
				.findFirst()
				.map(t -> Try.success(without(Schema.reservation.fact(
						resId, t._1.get(), t._2.get(), t._3.get()))))
				.orElseGet(() -> Try.failure(new IllegalStateException("no reservation: " + resId)));
	}

	public Try<Library> returnCopy(int loanId) {
		if (!hasActiveLoan(loanId)) {
			return Try.failure(new IllegalStateException("no active loan: " + loanId));
		}
		return Try.success(with(Schema.returned.fact(loanId)));
	}

	private boolean isMember(int memberId) {
		Unifiable<String> n = lvar();
		return Schema.member.exists(db, lval(memberId), n).solve(n).findAny().isPresent();
	}

	private boolean isAvailable(int copyId) {
		Unifiable<String> i = lvar();
		return rules.availableCopy.exists(lval(copyId), i).solve(i).findAny().isPresent();
	}

	private int activeLoanCount(int memberId) {
		Unifiable<Integer> n = lvar();
		return Aggregate.<Integer>count(l -> defer(() -> {
			Unifiable<Integer> c = lvar();
			Unifiable<Integer> d = lvar();
			return rules.activeLoan.exists(l, c, lval(memberId), d);
		}), n).solve(n).findFirst().map(Reified::get).orElse(0);
	}

	private boolean hasBook(String isbn) {
		Unifiable<String> t = lvar();
		Unifiable<String> a = lvar();
		return Schema.book.exists(db, lval(isbn), t, a).solve(t).findAny().isPresent();
	}

	private String isbnOf(int copyId) {
		Unifiable<String> i = lvar();
		return Schema.copy.exists(db, lval(copyId), i).solve(i)
				.findFirst().map(Reified::get)
				.orElseThrow(() -> new IllegalStateException("no such copy: " + copyId));
	}

	/** The member's reservation for the title, as the full removable fact. */
	private Optional<Fact> reservationOf(int memberId, String isbn) {
		Unifiable<Integer> r = lvar();
		Unifiable<Integer> d = lvar();
		return Schema.reservation.exists(db, r, lval(isbn), lval(memberId), d)
				.solve(lval(Tuple.of(r, d)))
				.map(Term::get)
				.findFirst()
				.map(t -> Schema.reservation.fact(t._1.get(), isbn, memberId, t._2.get()));
	}

	private Library withoutReservationOf(int memberId, String isbn) {
		return reservationOf(memberId, isbn).map(this::without).orElse(this);
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
		return values(Schema.copy.exists(db, c, lval(isbn)).solve(c));
	}

	public List<Integer> availableCopies(String isbn) {
		Unifiable<Integer> c = lvar();
		return values(rules.availableCopy.exists(c, lval(isbn)).solve(c));
	}

	public List<Integer> overdueLoans(int today) {
		Unifiable<Integer> l = lvar();
		return values(rules.overdue.exists(l, lval(today)).solve(l));
	}

	/**
	 * The queue head for a title: the member holding the reservation with the
	 * smallest id — reservation ids are issued in order, so min id is FIFO.
	 * The argmin is one goal: min binds the id, the join reads the member.
	 */
	public Optional<Integer> nextInQueue(String isbn) {
		Unifiable<Integer> minR = lvar();
		Unifiable<Integer> m = lvar();
		return Aggregate.min(r -> defer(() -> {
					Unifiable<Integer> m0 = lvar();
					Unifiable<Integer> d0 = lvar();
					return Schema.reservation.exists(db, r, lval(isbn), m0, d0);
				}), minR)
				.and(defer(() -> {
					Unifiable<Integer> d = lvar();
					return Schema.reservation.exists(db, minR, lval(isbn), m, d);
				}))
				.solve(m)
				.findFirst().map(Reified::get);
	}

	public List<String> titlesBy(String author) {
		Unifiable<String> t = lvar();
		Unifiable<String> i = lvar();
		return values(Schema.book.exists(db, i, t, lval(author)).solve(t));
	}

	private static <T> List<T> values(Stream<Reified<T>> answers) {
		return answers.map(Reified::get).collect(Collectors.toList());
	}
}
