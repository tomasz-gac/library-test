package com.tgac.library;

// ABOUTME: Reservations: a FIFO queue per title (argmin over reservation ids),
// ABOUTME: held titles only lend to the queue head, checkout fulfills the hold.

import static com.tgac.library.Days.day;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.Test;

public class ReservationTest {

	private static Library stocked() {
		return Library.empty()
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withCopy(2, "978-0")
				.withMember(100, "Ada", "standard")
				.withMember(101, "Alan", "standard")
				.withMember(102, "Kurt", "standard");
	}

	@Test
	public void reservationsQueueInFifoOrder() {
		Library lib = stocked()
				.reserve(900, "978-0", 101, day(3)).get()
				.reserve(901, "978-0", 100, day(3)).get();
		assertThat(lib.nextInQueue("978-0")).isEqualTo(Optional.of(101));
	}

	@Test
	public void anEmptyQueueHasNoHead() {
		assertThat(stocked().nextInQueue("978-0")).isEqualTo(Optional.empty());
	}

	@Test
	public void aMemberHoldsOneReservationPerTitle() {
		Library lib = stocked().reserve(900, "978-0", 101, day(3)).get();
		assertThat(lib.reserve(901, "978-0", 101, day(4)).isFailure()).isTrue();
	}

	@Test
	public void aReservedTitleIsHeldForTheQueueHead() {
		Library lib = stocked().reserve(900, "978-0", 101, day(3)).get();
		assertThat(lib.checkOut(500, 1, 100, day(30)).isFailure()).isTrue();
		assertThat(lib.checkOut(500, 1, 101, day(30)).isSuccess()).isTrue();
	}

	@Test
	public void checkoutFulfillsTheReservation() {
		Library lib = stocked()
				.reserve(900, "978-0", 101, day(3)).get()
				.reserve(901, "978-0", 100, day(4)).get()
				.checkOut(500, 1, 101, day(30)).get();
		assertThat(lib.nextInQueue("978-0")).isEqualTo(Optional.of(100));
	}

	@Test
	public void aClosedReservationDoesNotBlockANewOne() {
		Library cancelled = stocked()
				.reserve(900, "978-0", 101, day(3)).get()
				.cancelReservation(900).get();
		assertThat(cancelled.reserve(901, "978-0", 101, day(5)).isSuccess()).isTrue();
		Library fulfilled = stocked()
				.reserve(900, "978-0", 101, day(3)).get()
				.checkOut(500, 1, 101, day(30)).get();
		assertThat(fulfilled.reserve(901, "978-0", 101, day(5)).isSuccess()).isTrue();
	}

	@Test
	public void cancellingAdvancesTheQueue() {
		Library lib = stocked()
				.reserve(900, "978-0", 101, day(3)).get()
				.reserve(901, "978-0", 100, day(4)).get()
				.cancelReservation(900).get();
		assertThat(lib.nextInQueue("978-0")).isEqualTo(Optional.of(100));
	}
}
