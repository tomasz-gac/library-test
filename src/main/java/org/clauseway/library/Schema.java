package org.clauseway.library;

// ABOUTME: The library's relational schema as function-shaped definitions — one
// ABOUTME: method per base relation, names stated once, no relation constants.

import org.clauseway.logic.unification.Unifiable;
import org.clauseway.pldb.AnswerSource;
import java.time.LocalDate;
import org.clauseway.pldb.relations.Literal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Schema {

	/** A title in the catalog. */
	public static Literal book(AnswerSource db, Unifiable<String> isbn,
			Unifiable<String> title, Unifiable<String> author) {
		return Literal.relation(Schema.class, "book")
				.arg("isbn", isbn).indexed()
				.arg("title", title)
				.arg("author", author).indexed()
				.from(db);
	}

	/** A physical copy on the shelves. */
	public static Literal copy(AnswerSource db, Unifiable<Integer> copyId, Unifiable<String> isbn) {
		return Literal.relation(Schema.class, "copy")
				.arg("copyId", copyId).indexed()
				.arg("isbn", isbn).indexed()
				.from(db);
	}

	/** A registered member and the tier they hold. */
	public static Literal member(AnswerSource db, Unifiable<Integer> memberId, Unifiable<String> name,
			Unifiable<String> tier) {
		return Literal.relation(Schema.class, "member")
				.arg("memberId", memberId).indexed()
				.arg("name", name)
				.arg("tier", tier).indexed()
				.from(db);
	}

	/** A membership tier's lending policy: how many loans, for how long. */
	public static Literal tier(AnswerSource db, Unifiable<String> name, Unifiable<Integer> loanLimit,
			Unifiable<Long> loanDays) {
		return Literal.relation(Schema.class, "tier")
				.arg("name", name).indexed()
				.arg("loanLimit", loanLimit)
				.arg("loanDays", loanDays)
				.from(db);
	}

	/** A checkout event; stays forever, returns are separate events. */
	public static Literal loan(AnswerSource db, Unifiable<Integer> loanId, Unifiable<Integer> copyId,
			Unifiable<Integer> memberId, Unifiable<LocalDate> dueDay) {
		return Literal.relation(Schema.class, "loan")
				.arg("loanId", loanId).indexed()
				.arg("copyId", copyId).indexed()
				.arg("memberId", memberId).indexed()
				.arg("dueDay", dueDay)
				.from(db);
	}

	/** A return event closing a loan. */
	public static Literal returned(AnswerSource db, Unifiable<Integer> loanId) {
		return Literal.relation(Schema.class, "returned")
				.arg("loanId", loanId).indexed()
				.from(db);
	}

	/** A hold on a title; stays forever, cancellation and fulfillment are separate events. */
	public static Literal reservation(AnswerSource db, Unifiable<Integer> resId, Unifiable<String> isbn,
			Unifiable<Integer> memberId, Unifiable<LocalDate> day) {
		return Literal.relation(Schema.class, "reservation")
				.arg("resId", resId).indexed()
				.arg("isbn", isbn).indexed()
				.arg("memberId", memberId).indexed()
				.arg("day", day)
				.from(db);
	}

	/** A cancellation event closing a reservation. */
	public static Literal cancelled(AnswerSource db, Unifiable<Integer> resId) {
		return Literal.relation(Schema.class, "cancelled")
				.arg("resId", resId).indexed()
				.from(db);
	}

	/** A fulfillment event closing a reservation — its member checked the title out. */
	public static Literal fulfilled(AnswerSource db, Unifiable<Integer> resId) {
		return Literal.relation(Schema.class, "fulfilled")
				.arg("resId", resId).indexed()
				.from(db);
	}
}
