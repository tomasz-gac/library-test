package org.clauseway.library;

// ABOUTME: End-to-end domain tests for the library: catalog, inventory,
// ABOUTME: membership — facts in, relational queries out.

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class LibraryTest {

	private static Library stocked() {
		return Library.empty()
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withBook("978-1", "TAPL", "Pierce")
				.withCopy(1, "978-0")
				.withCopy(2, "978-0")
				.withCopy(3, "978-1")
				.withMember(100, "Ada", "standard")
				.withMember(101, "Alan", "standard");
	}

	@Test
	public void copiesAreInventoriedPerTitle() {
		assertThat(stocked().copiesOf("978-0")).containsExactlyInAnyOrder(1, 2);
		assertThat(stocked().copiesOf("978-1")).containsExactlyInAnyOrder(3);
		assertThat(stocked().copiesOf("978-9")).isEmpty();
	}

	@Test
	public void titlesAreFoundByAuthor() {
		assertThat(stocked().titlesBy("Abelson")).containsExactlyInAnyOrder("SICP");
		assertThat(stocked().titlesBy("Knuth")).isEmpty();
	}
}
