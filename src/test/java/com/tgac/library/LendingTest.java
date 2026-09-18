package com.tgac.library;

// ABOUTME: Lending lifecycle: checkout makes a copy unavailable, return restores
// ABOUTME: it — availability derived by negation over the loan/return events.

import static com.tgac.library.Days.day;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class LendingTest {

	private static Library stocked() {
		return Library.empty()
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withCopy(2, "978-0")
				.withMember(100, "Ada", "standard")
				.withMember(101, "Alan", "standard");
	}

	@Test
	public void lendingMakesACopyUnavailable() {
		Library lib = stocked();
		assertThat(lib.availableCopies("978-0")).containsExactlyInAnyOrder(1, 2);
		Library lent = lib.checkOut(500, 1, 100, day(30)).get();
		assertThat(lent.availableCopies("978-0")).containsExactlyInAnyOrder(2);
	}

	@Test
	public void returningRestoresAvailability() {
		Library lib = stocked()
				.checkOut(500, 1, 100, day(30)).get()
				.returnCopy(500).get();
		assertThat(lib.availableCopies("978-0")).containsExactlyInAnyOrder(1, 2);
	}

	@Test
	public void aLentCopyCannotBeLentAgain() {
		Library lib = stocked().checkOut(500, 1, 100, day(30)).get();
		assertThat(lib.checkOut(501, 1, 101, day(40)).isFailure()).isTrue();
	}

	@Test
	public void aReturnedCopyCanBeLentAgain() {
		Library lib = stocked()
				.checkOut(500, 1, 100, day(30)).get()
				.returnCopy(500).get();
		assertThat(lib.checkOut(501, 1, 101, day(40)).isSuccess()).isTrue();
	}

	@Test
	public void returnRequiresAnActiveLoan() {
		assertThat(stocked().returnCopy(999).isFailure()).isTrue();
		Library returnedOnce = stocked()
				.checkOut(500, 1, 100, day(30)).get()
				.returnCopy(500).get();
		assertThat(returnedOnce.returnCopy(500).isFailure()).isTrue();
	}

	@Test
	public void checkOutRequiresARegisteredMember() {
		assertThat(stocked().checkOut(500, 1, 999, day(30)).isFailure()).isTrue();
	}

	@Test
	public void checkOutRequiresAKnownCopy() {
		assertThat(stocked().checkOut(500, 99, 100, day(30)).isFailure()).isTrue();
	}
}
