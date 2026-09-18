package com.tgac.library;

// ABOUTME: Membership tiers: the loan limit and the loan length come from the
// ABOUTME: member's tier — policy rows joined into the denial and the due day.

import static com.tgac.library.Days.day;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class TierTest {

	private static Library stocked() {
		return Library.empty()
				.withTier("basic", 1, 7)
				.withTier("premium", 2, 30)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withCopy(2, "978-0")
				.withCopy(3, "978-0")
				.withCopy(4, "978-0")
				.withMember(100, "Ada", "basic")
				.withMember(101, "Alan", "premium");
	}

	@Test
	public void theLoanLimitComesFromTheMembersTier() {
		Library lib = stocked()
				.checkOut(500, 1, 100, day(10)).get()
				.checkOut(501, 2, 101, day(10)).get();
		assertThat(lib.checkOutDenials(100, 3)).containsExactlyInAnyOrder("at loan limit");
		assertThat(lib.checkOut(502, 3, 101, day(10)).isSuccess()).isTrue();
	}

	@Test
	public void theDueDayComesFromTheMembersTier() {
		Library lib = stocked()
				.checkOut(500, 1, 100, day(10)).get()
				.checkOut(501, 2, 101, day(10)).get();
		// basic lends for 7 days (due 17), premium for 30 (due 40)
		assertThat(lib.overdueLoans(day(17))).isEmpty();
		assertThat(lib.overdueLoans(day(18))).containsExactlyInAnyOrder(500);
		assertThat(lib.overdueLoans(day(41))).containsExactlyInAnyOrder(500, 501);
	}

	@Test
	public void aMemberWhoseTierHasNoPolicyIsDeniedByName() {
		Library lib = stocked().withMember(102, "Kurt", "gold");
		assertThat(lib.checkOutDenials(102, 1)).containsExactlyInAnyOrder("no loan policy");
		assertThat(lib.checkOut(500, 1, 102, day(10)).isFailure()).isTrue();
	}

	@Test
	public void anUnregisteredMemberIsNotAlsoDeniedForThePolicy() {
		assertThat(stocked().checkOutDenials(999, 1)).containsExactlyInAnyOrder("not a member");
	}
}
