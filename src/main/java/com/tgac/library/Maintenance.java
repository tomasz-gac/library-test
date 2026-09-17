package com.tgac.library;

// ABOUTME: The lifecycle face the domain facade deliberately lacks: compaction
// ABOUTME: sheds closed loan clusters through the certified removal door.

import static com.tgac.logic.unification.LVar.lvar;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.fibers.schedulers.BreadthFirstScheduler;
import com.tgac.logic.unification.Unifiable;
import com.tgac.pldb.inmemory.SharedDatabase;
import com.tgac.pldb.relations.Answer;
import com.tgac.pldb.relations.Question;
import com.tgac.pldb.transaction.AbstractTransaction;
import com.tgac.pldb.transaction.Simulated;
import com.tgac.pldb.transaction.Transaction;
import io.vavr.control.Try;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Administrative operations over the library's world — the doors
 * {@link Library} deliberately does not have. The domain stays
 * event-sourced and never deletes; the LIFECYCLE may shed spent
 * history. Compaction selects the clusters the domain's own closure
 * rule names ({@code closedLoan} — a loan with its return event),
 * retracts loan and return together, and commits under the certified
 * removal law: a pinned reader straddling the shed refuses, and every
 * LIVE query answers exactly as before — closed clusters are invisible
 * to liveness by definition.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Maintenance {

	public static Try<Nothing> compactClosedLoans(SharedDatabase world) throws Exception {
		try (Simulated tx = AbstractTransaction.over(world.open("compact-closed-loans"))) {
			Rules rules = new Rules(tx);
			Unifiable<Integer> id = lvar();
			Unifiable<Integer> copy = lvar();
			Unifiable<Integer> member = lvar();
			Unifiable<Integer> due = lvar();
			List<Answer> clusters = new BreadthFirstScheduler<>(Question.select(
					rules.closedLoan(id, copy, member, due),
					Schema.loan(null, id, copy, member, due),
					Schema.returned(null, id))).get();
			return tx.retracting(clusters).flatMap(Transaction::commit);
		}
	}
}
