package org.clauseway.library;

// ABOUTME: The tests' calendar: the old day indices mapped onto real dates, so
// ABOUTME: every arithmetic relationship between scenarios survives verbatim.

import java.time.LocalDate;

final class Days {

	private Days() {
	}

	static LocalDate day(int index) {
		return LocalDate.of(2026, 1, 1).plusDays(index);
	}
}
