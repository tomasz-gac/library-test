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
		return Literal.of("book", db)
				.indexed("isbn", isbn)
				.arg("title", title)
				.indexed("author", author);
	}

	/** A physical copy on the shelves. */
	public static Literal copy(AnswerSource db, Unifiable<Integer> copyId, Unifiable<String> isbn) {
		return Literal.of("copy", db)
				.indexed("copyId", copyId)
				.indexed("isbn", isbn);
	}

	/** A registered member. */
	public static Literal member(AnswerSource db, Unifiable<Integer> memberId, Unifiable<String> name) {
		return Literal.of("member", db)
				.indexed("memberId", memberId)
				.arg("name", name);
	}

	/** A checkout event; stays forever, returns are separate events. */
	public static Literal loan(AnswerSource db, Unifiable<Integer> loanId, Unifiable<Integer> copyId,
			Unifiable<Integer> memberId, Unifiable<Integer> dueDay) {
		return Literal.of("loan", db)
				.indexed("loanId", loanId)
				.indexed("copyId", copyId)
				.indexed("memberId", memberId)
				.arg("dueDay", dueDay);
	}

	/** A return event closing a loan. */
	public static Literal returned(AnswerSource db, Unifiable<Integer> loanId) {
		return Literal.of("returned", db).indexed("loanId", loanId);
	}

	/** A hold on a title; stays forever, cancellation and fulfillment are separate events. */
	public static Literal reservation(AnswerSource db, Unifiable<Integer> resId, Unifiable<String> isbn,
			Unifiable<Integer> memberId, Unifiable<Integer> day) {
		return Literal.of("reservation", db)
				.indexed("resId", resId)
				.indexed("isbn", isbn)
				.indexed("memberId", memberId)
				.arg("day", day);
	}

	/** A cancellation event closing a reservation. */
	public static Literal cancelled(AnswerSource db, Unifiable<Integer> resId) {
		return Literal.of("cancelled", db).indexed("resId", resId);
	}

	/** A fulfillment event closing a reservation — its member checked the title out. */
	public static Literal fulfilled(AnswerSource db, Unifiable<Integer> resId) {
		return Literal.of("fulfilled", db).indexed("resId", resId);
	}
}
