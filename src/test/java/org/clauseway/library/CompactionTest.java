package org.clauseway.library;

// ABOUTME: Compaction receipts: live queries answer identically across the shed,
// ABOUTME: history shrinks deliberately, a pinned reader straddling it bounces.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.clauseway.library.Days.day;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.AbstractTransaction;
import org.clauseway.pldb.transaction.Transaction;
import io.vavr.control.Try;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class CompactionTest {

	private static Library open(SharedDatabase store, String id) {
		return Library.over(AbstractTransaction.over(store.open(id)));
	}

	/** Loan 500 lent and RETURNED (closed), loan 501 still active. */
	private static SharedDatabase storeWithOneClosedLoan() {
		SharedDatabase store = SharedDatabase.empty();
		assertThat(open(store, "seed")
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withCopy(2, "978-0")
				.withMember(100, "Ada", "standard")
				.withMember(101, "Alan", "standard")
				.commit().isSuccess()).isTrue();
		assertThat(open(store, "lend-500").checkOut(500, 1, 100, day(10)).get()
				.commit().isSuccess()).isTrue();
		assertThat(open(store, "lend-501").checkOut(501, 2, 101, day(10)).get()
				.commit().isSuccess()).isTrue();
		assertThat(open(store, "return-500").returnCopy(500).get()
				.commit().isSuccess()).isTrue();
		return store;
	}

	private static long loanEvents(SharedDatabase store) throws Exception {
		try (Transaction reader = AbstractTransaction.over(store.open("count-loans"))) {
			Unifiable<Integer> id = lvar();
			return Schema.loan(reader, id, lvar(), lvar(), lvar()).solve(id).count();
		}
	}

	@Test
	public void liveQueriesAnswerIdenticallyAcrossTheShed() throws Exception {
		SharedDatabase store = storeWithOneClosedLoan();

		Library before = open(store, "before");
		List<Integer> availableBefore = before.availableCopies("978-0");
		List<Integer> adasBefore = before.activeLoansOf(100);
		List<Integer> alansBefore = before.activeLoansOf(101);
		List<String> denialsBefore = before.checkOutDenials(100, 2);

		assertThat(Maintenance.compactClosedLoans(store).isSuccess()).isTrue();

		Library after = open(store, "after");
		assertThat(after.availableCopies("978-0"))
				.describedAs("liveness cannot see a closed cluster — the shed changes nothing live")
				.isEqualTo(availableBefore);
		assertThat(after.activeLoansOf(100)).isEqualTo(adasBefore);
		assertThat(after.activeLoansOf(101)).isEqualTo(alansBefore);
		assertThat(after.checkOutDenials(100, 2)).isEqualTo(denialsBefore);
	}

	@Test
	public void historyShrinksDeliberately() throws Exception {
		SharedDatabase store = storeWithOneClosedLoan();
		assertThat(loanEvents(store)).isEqualTo(2);

		assertThat(Maintenance.compactClosedLoans(store).isSuccess()).isTrue();

		assertThat(loanEvents(store))
				.describedAs("the closed cluster left the base relations — history queries change, by design")
				.isEqualTo(1);
	}

	@Test
	public void compactionWithNothingClosedIsANoOp() throws Exception {
		SharedDatabase store = SharedDatabase.empty();
		assertThat(open(store, "seed")
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withMember(100, "Ada", "standard")
				.commit().isSuccess()).isTrue();
		assertThat(open(store, "lend").checkOut(500, 1, 100, day(10)).get()
				.commit().isSuccess()).isTrue();

		assertThat(Maintenance.compactClosedLoans(store).isSuccess()).isTrue();
		assertThat(loanEvents(store)).isEqualTo(1);
	}

	@Test
	public void aPinnedReaderStraddlingTheShedBounces() throws Exception {
		SharedDatabase store = storeWithOneClosedLoan();
		Transaction reader = AbstractTransaction.over(store.open("reader"));
		Unifiable<Integer> id = lvar();
		assertThat(Schema.loan(reader, id, lvar(), lvar(), lvar()).solve(id).count())
				.isEqualTo(2);

		assertThat(Maintenance.compactClosedLoans(store).isSuccess()).isTrue();

		Try<?> refused = reader.asserting(Collections.singletonList(
				Schema.book(null, lval("978-9"), lval("Tar Pit"), lval("Moseley"))))
				.get().commit();
		assertThat(refused.getCause())
				.describedAs("the pinned loan region lost its cluster — the certified shed divides pins")
				.isInstanceOf(Transaction.Conflict.class);
	}
}
