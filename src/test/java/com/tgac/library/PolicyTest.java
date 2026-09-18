package com.tgac.library;

// ABOUTME: Lending policy: overdue = due-day before today (FD comparison over
// ABOUTME: the derived loans), borrow limit = count of active loans per member.

import static com.tgac.logic.unification.LVal.lval;
import static com.tgac.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.logic.unification.Reified;
import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.inmemory.SharedDatabase;
import com.tgac.pldb.transaction.AbstractTransaction;
import com.tgac.pldb.transaction.Transaction;
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
		Transaction t1 = t0.asserting(Schema.tier(t0, lval("standard"), lval(3), lval(14))).get();
		Transaction t2 = t1.asserting(Schema.member(t1, lval(100), lval("Ada"), lval("standard"))).get();
		Rules rules = new Rules(t2);

		Unifiable<Integer> day = lvar();
		List<Integer> days = rules.dueDate(lval(100), day, lval(44)).solve(day)
				.map(Reified::get)
				.collect(Collectors.toList());

		assertThat(days).containsExactly(30);
	}

	@Test
	public void overdueLoansAreThoseDueBeforeToday() {
		// standard lends for 14 days: checkouts on 30 and 60 fall due 44 and 74
		Library lib = stocked()
				.checkOut(500, 1, 100, 30).get()
				.checkOut(501, 2, 100, 60).get();
		assertThat(lib.overdueLoans(45)).containsExactlyInAnyOrder(500);
		assertThat(lib.overdueLoans(20)).isEmpty();
		assertThat(lib.overdueLoans(75)).containsExactlyInAnyOrder(500, 501);
	}

	@Test
	public void returnedLoansAreNeverOverdue() {
		Library lib = stocked()
				.checkOut(500, 1, 100, 30).get()
				.returnCopy(500).get();
		assertThat(lib.overdueLoans(45)).isEmpty();
	}

	@Test
	public void aMemberAtTheLoanLimitCannotBorrowMore() {
		Library lib = stocked()
				.checkOut(500, 1, 100, 30).get()
				.checkOut(501, 2, 100, 30).get()
				.checkOut(502, 3, 100, 30).get();
		assertThat(lib.checkOut(503, 4, 100, 30).isFailure()).isTrue();
		assertThat(lib.checkOut(503, 4, 101, 30).isSuccess()).isTrue();
	}

	@Test
	public void returningFreesTheLimit() {
		Library lib = stocked()
				.checkOut(500, 1, 100, 30).get()
				.checkOut(501, 2, 100, 30).get()
				.checkOut(502, 3, 100, 30).get()
				.returnCopy(500).get();
		assertThat(lib.checkOut(503, 4, 100, 30).isSuccess()).isTrue();
	}
}
