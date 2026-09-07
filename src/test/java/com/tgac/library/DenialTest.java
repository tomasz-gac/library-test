package com.tgac.library;

// ABOUTME: The denial relation: checkout policy as the enumeration of its own
// ABOUTME: complement — every violated rule is a positive answer with a name.

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DenialTest {

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
	public void aCleanCheckoutHasNoDenials() {
		assertThat(stocked().checkOutDenials(100, 1)).isEmpty();
	}

	@Test
	public void everyViolatedRuleIsNamed() {
		Library lib = stocked()
				.checkOut(500, 1, 100, 30).get()
				.checkOut(501, 2, 100, 30).get()
				.checkOut(502, 3, 100, 30).get()
				.reserve(900, "978-0", 101, 3).get();
		assertThat(lib.checkOutDenials(100, 4)).containsExactlyInAnyOrder(
				"at loan limit",
				"title held for another member");
	}

	@Test
	public void unknownMemberAndUnknownCopyBothReport() {
		assertThat(stocked().checkOutDenials(999, 99)).containsExactlyInAnyOrder(
				"not a member",
				"no such copy");
	}

	@Test
	public void aLentCopyIsDeniedAsUnavailable() {
		Library lib = stocked().checkOut(500, 1, 100, 30).get();
		assertThat(lib.checkOutDenials(101, 1)).containsExactlyInAnyOrder(
				"copy not available");
	}

	@Test
	public void theQueueHeadIsNotDenied() {
		Library lib = stocked().reserve(900, "978-0", 101, 3).get();
		assertThat(lib.checkOutDenials(101, 1)).isEmpty();
		assertThat(lib.checkOutDenials(100, 1)).containsExactlyInAnyOrder(
				"title held for another member");
	}

	@Test
	public void aCleanReserveHasNoDenials() {
		assertThat(stocked().reserveDenials(100, "978-0")).isEmpty();
	}

	@Test
	public void reserveViolationsAreNamed() {
		assertThat(stocked().reserveDenials(999, "978-9")).containsExactlyInAnyOrder(
				"not a member",
				"no such title");
	}

	@Test
	public void aDuplicateReservationIsDeniedByName() {
		Library lib = stocked().reserve(900, "978-0", 101, 3).get();
		assertThat(lib.reserveDenials(101, "978-0")).containsExactlyInAnyOrder(
				"already holds a reservation");
	}

	@Test
	public void theFailureCarriesEveryReason() {
		Library lib = stocked()
				.checkOut(500, 1, 100, 30).get()
				.checkOut(501, 2, 100, 30).get()
				.checkOut(502, 3, 100, 30).get()
				.reserve(900, "978-0", 101, 3).get();
		Throwable failure = lib.checkOut(503, 4, 100, 30).getCause();
		assertThat(failure.getMessage()).contains("at loan limit");
		assertThat(failure.getMessage()).contains("title held for another member");
	}
}
