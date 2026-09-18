package com.tgac.library;

// ABOUTME: The server receipts: pinned GETs carry their footprint, premised POSTs
// ABOUTME: bounce when the client's world moved, denials stay the server's own.

import static com.tgac.library.Days.day;
import static org.assertj.core.api.Assertions.assertThat;

import com.tgac.functional.category.Nothing;
import com.tgac.pldb.inmemory.SharedDatabase;
import com.tgac.pldb.transaction.AbstractTransaction;
import com.tgac.pldb.transaction.Footprint;
import com.tgac.pldb.transaction.Pinned;
import com.tgac.pldb.transaction.Transaction;
import io.vavr.control.Try;
import java.util.List;
import org.junit.Test;

public class LibraryServerTest {

	private static final int SALLY = 100;
	private static final int WATCHER = 101;
	private static final int BOB = 102;

	/** Two titles, three members; Sally holds loan 500 on copy 1. */
	private static SharedDatabase stockedWorld() {
		SharedDatabase world = SharedDatabase.empty();
		assertThat(Library.over(AbstractTransaction.over(world.open("seed")))
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withBook("978-1", "Out of the Tar Pit", "Moseley")
				.withCopy(2, "978-1")
				.withMember(SALLY, "Sally", "standard")
				.withMember(WATCHER, "Watts", "standard")
				.withMember(BOB, "Bob", "standard")
				.checkOut(500, 1, SALLY, day(1)).get()
				.commit().isSuccess()).isTrue();
		return world;
	}

	@Test
	public void aPinnedGetRoundTripsIntoALandedPost() {
		LibraryServer server = LibraryServer.over(stockedWorld());

		Pinned<List<Integer>> available = server.availableCopies("978-1");
		assertThat(available.getValue()).containsExactly(2);

		assertThat(server.checkOut(501, 2, WATCHER, day(2), (Footprint) available.getPin())
				.isSuccess()).isTrue();
		assertThat(server.availableCopies("978-1").getValue()).isEmpty();
	}

	@Test
	public void theWatcherLendsWhatSallyReturnedOnlyIfHerLoansStoodStill() {
		LibraryServer server = LibraryServer.over(stockedWorld());

		// Sally returns copy 1; the watcher polls her loans and sees none —
		// the pinned answer's footprint carries the loan AND returned regions
		// the derived activeLoan read, empties included
		assertThat(server.returnCopy(500, Footprint.empty()).isSuccess()).isTrue();
		Pinned<List<Integer>> sallys = server.activeLoansOf(SALLY);
		assertThat(sallys.getValue()).isEmpty();

		// Bob's unrelated checkout lands in between — different member,
		// different copy — and still moves the loan relation's world
		assertThat(server.checkOut(501, 2, BOB, day(2), Footprint.empty()).isSuccess()).isTrue();

		// the conditional lend: copy 1 only if Sally lent nothing since the
		// poll — the premise is stale at RELATION grain (Bob's loan moved
		// it), the deliberate coarseness of the in-memory marks
		Try<Nothing> refused = server.checkOut(502, 1, WATCHER, day(2), (Footprint) sallys.getPin());
		assertThat(refused.isFailure()).isTrue();
		assertThat(refused.getCause()).isInstanceOf(Transaction.Conflict.class);

		// re-poll, re-decide, retry: the fresh premise lands
		Pinned<List<Integer>> fresh = server.activeLoansOf(SALLY);
		assertThat(fresh.getValue()).isEmpty();
		assertThat(server.checkOut(502, 1, WATCHER, day(2), (Footprint) fresh.getPin())
				.isSuccess()).isTrue();
	}

	@Test
	public void theServersOwnValidationRefusesIndependentlyOfThePremise() {
		LibraryServer server = LibraryServer.over(stockedWorld());

		// a perfectly fresh premise cannot buy a copy that is actively lent:
		// the denial is the validation solve's, not the certify's
		Pinned<List<Integer>> fresh = server.activeLoansOf(SALLY);
		Try<Nothing> denied = server.checkOut(503, 1, WATCHER, day(2), (Footprint) fresh.getPin());
		assertThat(denied.isFailure()).isTrue();
		assertThat(denied.getCause())
				.isNotInstanceOf(Transaction.Conflict.class)
				.hasMessageContaining("copy not available");
	}
}
