package com.tgac.library;

// ABOUTME: Lending policy: overdue = due-day before today (FD comparison over
// ABOUTME: the derived loans), borrow limit = count of active loans per member.

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class PolicyTest {

	private static Library stocked() {
		return Library.empty()
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withCopy(2, "978-0")
				.withCopy(3, "978-0")
				.withCopy(4, "978-0")
				.withMember(100, "Ada")
				.withMember(101, "Alan");
	}

	@Test
	public void overdueLoansAreThoseDueBeforeToday() {
		Library lib = stocked()
				.checkOut(500, 1, 100, 30).get()
				.checkOut(501, 2, 100, 60).get();
		assertThat(lib.overdueLoans(45)).containsExactlyInAnyOrder(500);
		assertThat(lib.overdueLoans(20)).isEmpty();
		assertThat(lib.overdueLoans(61)).containsExactlyInAnyOrder(500, 501);
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
