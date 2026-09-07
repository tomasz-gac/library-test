package com.tgac.library;

// ABOUTME: The library's relational schema as function-shaped definitions — one
// ABOUTME: method per base relation, names stated once, no relation constants.

import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.AnswerSource;
import com.tgac.pldb.relations.Literal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Schema {

	/** A title in the catalog. */
	public static Literal book(AnswerSource db, Unifiable<String> isbn,
			Unifiable<String> title, Unifiable<String> author) {
		return Literal.relation("book")
				.arg("isbn", isbn).indexed()
				.arg("title", title)
				.arg("author", author).indexed()
				.from(db);
	}

	/** A physical copy on the shelves. */
	public static Literal copy(AnswerSource db, Unifiable<Integer> copyId, Unifiable<String> isbn) {
		return Literal.relation("copy")
				.arg("copyId", copyId).indexed()
				.arg("isbn", isbn).indexed()
				.from(db);
	}

	/** A registered member. */
	public static Literal member(AnswerSource db, Unifiable<Integer> memberId, Unifiable<String> name) {
		return Literal.relation("member")
				.arg("memberId", memberId).indexed()
				.arg("name", name)
				.from(db);
	}

	/** A checkout event; stays forever, returns are separate events. */
	public static Literal loan(AnswerSource db, Unifiable<Integer> loanId, Unifiable<Integer> copyId,
			Unifiable<Integer> memberId, Unifiable<Integer> dueDay) {
		return Literal.relation("loan")
				.arg("loanId", loanId).indexed()
				.arg("copyId", copyId).indexed()
				.arg("memberId", memberId).indexed()
				.arg("dueDay", dueDay)
				.from(db);
	}

	/** A return event closing a loan. */
	public static Literal returned(AnswerSource db, Unifiable<Integer> loanId) {
		return Literal.relation("returned")
				.arg("loanId", loanId).indexed()
				.from(db);
	}

	/** A hold on a title; stays forever, cancellation and fulfillment are separate events. */
	public static Literal reservation(AnswerSource db, Unifiable<Integer> resId, Unifiable<String> isbn,
			Unifiable<Integer> memberId, Unifiable<Integer> day) {
		return Literal.relation("reservation")
				.arg("resId", resId).indexed()
				.arg("isbn", isbn).indexed()
				.arg("memberId", memberId).indexed()
				.arg("day", day)
				.from(db);
	}

	/** A cancellation event closing a reservation. */
	public static Literal cancelled(AnswerSource db, Unifiable<Integer> resId) {
		return Literal.relation("cancelled")
				.arg("resId", resId).indexed()
				.from(db);
	}

	/** A fulfillment event closing a reservation — its member checked the title out. */
	public static Literal fulfilled(AnswerSource db, Unifiable<Integer> resId) {
		return Literal.relation("fulfilled")
				.arg("resId", resId).indexed()
				.from(db);
	}
}
