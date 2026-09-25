package org.clauseway.library;

// ABOUTME: The library over a transaction: commits persist across reopenings, and
// ABOUTME: the double checkout meets the conflict and re-solves to an ordinary denial.

import static org.clauseway.library.Days.day;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.AbstractTransaction;
import org.clauseway.pldb.transaction.Transaction;
import org.clauseway.vavr.control.Try;
import org.junit.Test;

public class CommitTest {

	private static Library open(SharedDatabase store, String id) {
		return Library.over(AbstractTransaction.over(store.open(id)));
	}

	private static SharedDatabase stockedStore() {
		SharedDatabase store = SharedDatabase.empty();
		assertThat(open(store, "seed")
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withMember(100, "Ada", "standard")
				.withMember(101, "Alan", "standard")
				.commit().isSuccess()).isTrue();
		return store;
	}

	@Test
	public void aCommitPersistsForTheNextTransaction() {
		SharedDatabase store = stockedStore();
		Library lending = open(store, "lend");
		assertThat(lending.availableCopies("978-0")).containsExactly(1);
		assertThat(lending.checkOut(500, 1, 100, day(10)).get().commit().isSuccess()).isTrue();

		Library after = open(store, "after");
		assertThat(after.availableCopies("978-0")).isEmpty();
		assertThat(after.overdueLoans(day(25))).containsExactly(500);
	}

	@Test
	public void anAbandonedLibraryLeavesNoTrace() {
		SharedDatabase store = stockedStore();
		open(store, "abandoned").checkOut(500, 1, 100, day(10)).get();

		assertThat(open(store, "after").availableCopies("978-0")).containsExactly(1);
	}

	@Test
	public void theDoubleCheckoutMeetsTheConflictAndResolvesToADenial() {
		// the write-skew story at the domain level: both libraries validate
		// against their own snapshot, both see copy 1 available, the first
		// commit wins, the second is REFUSED at commit — and the re-solve
		// turns the anomaly into an ordinary named denial
		SharedDatabase store = stockedStore();
		Library ada = open(store, "ada").checkOut(500, 1, 100, day(10)).get();
		Library alan = open(store, "alan").checkOut(501, 1, 101, day(10)).get();

		assertThat(ada.commit().isSuccess()).isTrue();
		Try<?> refused = alan.commit();
		assertThat(refused.isFailure()).isTrue();
		assertThat(refused.getCause()).isInstanceOf(Transaction.Conflict.class);

		assertThat(open(store, "alan-retry").checkOutDenials(101, 1))
				.describedAs("the retry sees the landed loan and answers with the denial")
				.containsExactly("copy not available");
	}
}
