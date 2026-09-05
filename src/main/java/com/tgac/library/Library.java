package com.tgac.library;

// ABOUTME: The library domain facade — an immutable fact base with commands
// ABOUTME: that append events and queries answered relationally by the engine.

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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Library {

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

	// -- commands --------------------------------------------------------

	public Try<Library> checkOut(int loanId, int copyId, int memberId, int dueDay) {
		if (!isMember(memberId)) {
			return Try.failure(new IllegalStateException("not a member: " + memberId));
		}
		if (!isAvailable(copyId)) {
			return Try.failure(new IllegalStateException("copy not available: " + copyId));
		}
		return Try.success(with(Schema.loan.fact(loanId, copyId, memberId, dueDay)));
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

	public List<String> titlesBy(String author) {
		Unifiable<String> t = lvar();
		Unifiable<String> i = lvar();
		return values(Schema.book.exists(db, i, t, lval(author)).solve(t));
	}

	private static <T> List<T> values(Stream<Reified<T>> answers) {
		return answers.map(Reified::get).collect(Collectors.toList());
	}
}
