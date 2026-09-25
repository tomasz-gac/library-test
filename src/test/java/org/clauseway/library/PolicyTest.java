package org.clauseway.library;

// ABOUTME: Lending policy: overdue = due-day before today (FD comparison over
// ABOUTME: the derived loans), borrow limit = count of active loans per member.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.library.Days.day;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.unification.terms.Reified;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.AbstractTransaction;
import org.clauseway.pldb.transaction.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class PolicyTest {

	private static Library stocked() {
		return Library.empty()
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withCopy(2, "978-0")
				.withCopy(3, "978-0")
				.withCopy(4, "978-0")
				.withMember(100, "Ada", "standard")
				.withMember(101, "Alan", "standard");
	}

	@Test
	public void theCheckoutDayFallsOutOfTheDueDate() {
		// day + loanDays = dueDay is a relation, not a projection: given the
		// due day, the checkout day falls out backwards through the same rule
		Transaction t0 = AbstractTransaction.over(SharedDatabase.empty().open("policy-backwards"));
		Transaction t1 = t0.asserting(Schema.tier(t0, lval("standard"), lval(3), lval(14L)));
		Transaction t2 = t1.asserting(Schema.member(t1, lval(100), lval("Ada"), lval("standard")));
		Rules rules = new Rules(t2);

		Unifiable<LocalDate> checkout = lvar();
		List<LocalDate> checkouts = rules.dueDate(lval(100), checkout, lval(day(44))).solve(checkout)
				.map(Reified::get)
				.collect(Collectors.toList());

		assertThat(checkouts).containsExactly(day(30));
	}

	@Test
	public void overdueLoansAreThoseDueBeforeToday() {
		// standard lends for 14 days: checkouts on 30 and 60 fall due 44 and 74
		Library lib = stocked()
				.checkOut(500, 1, 100, day(30)).get()
				.checkOut(501, 2, 100, day(60)).get();
		assertThat(lib.overdueLoans(day(45))).containsExactlyInAnyOrder(500);
		assertThat(lib.overdueLoans(day(20))).isEmpty();
		assertThat(lib.overdueLoans(day(75))).containsExactlyInAnyOrder(500, 501);
	}

	@Test
	public void returnedLoansAreNeverOverdue() {
		Library lib = stocked()
				.checkOut(500, 1, 100, day(30)).get()
				.returnCopy(500).get();
		assertThat(lib.overdueLoans(day(45))).isEmpty();
	}

	@Test
	public void aMemberAtTheLoanLimitCannotBorrowMore() {
		Library lib = stocked()
				.checkOut(500, 1, 100, day(30)).get()
				.checkOut(501, 2, 100, day(30)).get()
				.checkOut(502, 3, 100, day(30)).get();
		assertThat(lib.checkOut(503, 4, 100, day(30)).isFailure()).isTrue();
		assertThat(lib.checkOut(503, 4, 101, day(30)).isSuccess()).isTrue();
	}

	@Test
	public void returningFreesTheLimit() {
		Library lib = stocked()
				.checkOut(500, 1, 100, day(30)).get()
				.checkOut(501, 2, 100, day(30)).get()
				.checkOut(502, 3, 100, day(30)).get()
				.returnCopy(500).get();
		assertThat(lib.checkOut(503, 4, 100, day(30)).isSuccess()).isTrue();
	}
}
