package org.clauseway.library;

// ABOUTME: Pins the clauseway.org post "The half of the Tar Pit we skipped"
// ABOUTME: against the living stack -- every snippet compiles and answers as
// ABOUTME: the post claims, so the published demo cannot silently rot.

import static org.clauseway.logic.unification.terms.LVal.lval;
import static org.clauseway.logic.unification.terms.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.clauseway.logic.solving.Query;
import org.clauseway.logic.unification.terms.Term;
import org.clauseway.logic.unification.terms.Unifiable;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.AbstractTransaction;
import org.clauseway.pldb.transaction.Transaction;
import org.junit.Test;

public class TarPitPostTest {

	/** The post's world: two copies, member 100 holding copy 1 on loan 10. */
	private static Transaction world() {
		Transaction t = AbstractTransaction.over(SharedDatabase.empty().open("tar-pit-post"));
		t = t.asserting(Schema.copy(t, lval(1), lval("978-0132350884")));
		t = t.asserting(Schema.copy(t, lval(2), lval("978-0201633610")));
		t = t.asserting(Schema.loan(t, lval(10), lval(1), lval(100),
				lval(LocalDate.of(2026, 10, 1))));
		return t;
	}

	@Test
	public void whoHoldsCopyOne() {
		Rules rules = new Rules(world());
		Unifiable<Integer> who = lvar();
		List<Integer> held = Query.of(rules.activeLoan(lvar(), lval(1), who, lvar()))
				.solve(who)
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(held).containsExactly(100);
	}

	@Test
	public void whichCopiesDoesMemberHold() {
		Rules rules = new Rules(world());
		Unifiable<Integer> what = lvar();
		List<Integer> copies = Query.of(rules.activeLoan(lvar(), what, lval(100), lvar()))
				.solve(what)
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(copies).containsExactly(1);
	}

	@Test
	public void availableCopyNegatesTheDerivedRelation() {
		// derived from derived: availableCopy excludes copies the DERIVED
		// activeLoan claims -- copy 1 is out, copy 2 is free
		Rules rules = new Rules(world());
		Unifiable<Integer> copyId = lvar();
		List<Integer> available = Query.of(rules.availableCopy(copyId, lvar()))
				.solve(copyId)
				.map(Term::get)
				.collect(Collectors.toList());
		assertThat(available).containsExactly(2);
	}

	@Test
	public void aReturnEventClosesTheLoanWithoutAnyFlag() {
		// no active flag flipped anywhere: asserting the return EVENT is the
		// whole write, and activeLoan/availableCopy re-derive
		Transaction t = world();
		Transaction after = t.asserting(Schema.returned(t, lval(10)));
		Rules rules = new Rules(after);

		Unifiable<Integer> who = lvar();
		assertThat(Query.of(rules.activeLoan(lvar(), lval(1), who, lvar()))
				.solve(who).count()).isEqualTo(0);

		Unifiable<Integer> copyId = lvar();
		assertThat(Query.of(rules.availableCopy(copyId, lvar()))
				.solve(copyId).map(Term::get).collect(Collectors.toList()))
				.containsExactlyInAnyOrder(1, 2);
	}
}
