package org.clauseway.library;

// ABOUTME: The library exposed server-ish: GET-shaped reads answer with their
// ABOUTME: footprint attached, POST-shaped commands certify the client's premise.

import org.clauseway.functional.Nothing;
import org.clauseway.pldb.inmemory.SharedDatabase;
import org.clauseway.pldb.transaction.AbstractTransaction;
import org.clauseway.pldb.transaction.Footprint;
import org.clauseway.pldb.transaction.Pinned;
import org.clauseway.pldb.transaction.Simulated;
import org.clauseway.vavr.control.Try;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The library's wire shape, function-level: the server owns the world and
 * the engine, clients never solve. A GET opens a snapshot, answers the
 * business read, and attaches the {@link Simulated#footprint()} of that
 * read — the client's proof material. A POST runs the command's OWN
 * validation solve in a fresh transaction and commits it
 * {@link Simulated#requiring(Footprint) requiring} the client's premise:
 * the pins of whatever earlier reads the client's decision stood on.
 * Refusal is the two guards, distinctly: a denial names the violated
 * rule (the server's validation), a {@code Conflict} means a premised
 * world moved (re-read, re-solve). The server holds no per-client
 * state — the echoed premise IS the session.
 */
public final class LibraryServer {

	private final SharedDatabase world;
	private final AtomicLong requests = new AtomicLong();

	private LibraryServer(SharedDatabase world) {
		this.world = world;
	}

	public static LibraryServer over(SharedDatabase world) {
		return new LibraryServer(world);
	}

	// -- GET faces: pinned outputs ---------------------------------------

	public Pinned<List<Integer>> activeLoansOf(int memberId) {
		return reading("loans-of", library -> library.activeLoansOf(memberId));
	}

	public Pinned<List<Integer>> availableCopies(String isbn) {
		return reading("available", library -> library.availableCopies(isbn));
	}

	// -- POST faces: premise-certified commands --------------------------

	public Try<Nothing> checkOut(int loanId, int copyId, int memberId, LocalDate day, Footprint premise) {
		return posting("checkout", premise,
				library -> library.checkOut(loanId, copyId, memberId, day));
	}

	public Try<Nothing> returnCopy(int loanId, Footprint premise) {
		return posting("return", premise, library -> library.returnCopy(loanId));
	}

	private <T> Pinned<T> reading(String endpoint, Function<Library, T> query) {
		Simulated read = AbstractTransaction.over(world.open(request(endpoint)));
		try (Library library = Library.over(read)) {
			return Pinned.of(query.apply(library), read.footprint());
		} catch (Exception e) {
			throw new IllegalStateException(endpoint + " failed", e);
		}
	}

	private Try<Nothing> posting(String endpoint, Footprint premise,
			Function<Library, Try<Library>> command) {
		try {
			Simulated write = AbstractTransaction.over(world.open(request(endpoint))).requiring(premise);
			try (Library library = Library.over(write)) {
				return command.apply(library).flatMap(Library::commit);
			}
		} catch (Exception e) {
			return Try.failure(e);
		}
	}

	private String request(String endpoint) {
		return endpoint + "#" + requests.incrementAndGet();
	}
}
