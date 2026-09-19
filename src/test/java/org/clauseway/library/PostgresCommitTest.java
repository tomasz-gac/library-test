package org.clauseway.library;

// ABOUTME: The commit receipts against real PostgreSQL, on BOTH serializations —
// ABOUTME: native (SSI) and simulated (watermark) — through the unchanged domain.

import static org.clauseway.library.Days.day;
import static org.clauseway.logic.unification.LVar.lvar;
import static org.assertj.core.api.Assertions.assertThat;

import org.clauseway.pldb.sql.SqlFetch;
import org.clauseway.pldb.sql.SerializableSource;
import org.clauseway.pldb.sql.Watermark;
import org.clauseway.pldb.transaction.AbstractTransaction;
import org.clauseway.pldb.transaction.Transaction;
import io.vavr.control.Try;
import java.sql.Connection;
import java.time.LocalDate;
import java.sql.Date;
import org.clauseway.pldb.sql.Codec;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresCommitTest {

	private static PostgreSQLContainer<?> postgres;

	@BeforeClass
	public static void startPostgres() {
		Assume.assumeTrue("Docker unavailable — the PG lane needs it",
				DockerClientFactory.instance().isDockerAvailable());
		postgres = new PostgreSQLContainer<>("postgres:16-alpine");
		postgres.start();
	}

	@AfterClass
	public static void stopPostgres() {
		if (postgres != null) {
			postgres.stop();
		}
	}

	@Before
	public void resetSchema() throws SQLException {
		try (Connection admin = connect(); Statement ddl = admin.createStatement()) {
			for (String table : Arrays.asList("tier", "book", "copy", "member", "loan",
					"returned", "reservation", "cancelled", "fulfilled", "watermark")) {
				ddl.execute("DROP TABLE IF EXISTS " + table);
			}
			ddl.execute("CREATE TABLE tier(name VARCHAR(32) NOT NULL,"
					+ " loanLimit INT NOT NULL, loanDays BIGINT NOT NULL)");
			ddl.execute("CREATE TABLE book(isbn VARCHAR(32) NOT NULL,"
					+ " title VARCHAR(64) NOT NULL, author VARCHAR(64) NOT NULL)");
			ddl.execute("CREATE TABLE copy(copyId INT NOT NULL, isbn VARCHAR(32) NOT NULL)");
			ddl.execute("CREATE TABLE member(memberId INT NOT NULL,"
					+ " name VARCHAR(64) NOT NULL, tier VARCHAR(32) NOT NULL)");
			ddl.execute("CREATE TABLE loan(loanId INT NOT NULL, copyId INT NOT NULL,"
					+ " memberId INT NOT NULL, dueDay DATE NOT NULL)");
			ddl.execute("CREATE TABLE returned(loanId INT NOT NULL)");
			ddl.execute("CREATE TABLE reservation(resId INT NOT NULL, isbn VARCHAR(32) NOT NULL,"
					+ " memberId INT NOT NULL, day DATE NOT NULL)");
			ddl.execute("CREATE TABLE cancelled(resId INT NOT NULL)");
			ddl.execute("CREATE TABLE fulfilled(resId INT NOT NULL)");
			Watermark.schema(ddl);
			admin.commit();
		}
	}

	/** Every minted connection, closed after each test — an idle-in-transaction
	 * snapshot would block the next DROP TABLE forever. */
	private static final List<Connection> connections = new ArrayList<>();

	private static Connection connect() throws SQLException {
		Connection connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		connection.setAutoCommit(false);
		connections.add(connection);
		return connection;
	}

	@After
	public void closeConnections() {
		for (Connection connection : connections) {
			try {
				connection.close();
			} catch (SQLException suppressed) {
				// already closed by its transaction — the list over-collects on purpose
			}
		}
		connections.clear();
	}

	// -- the two serializations, same domain -----------------------------

	/** DATE columns cross the wire as java.sql.Date; the value vocabulary is LocalDate. */
	private static final Codec<LocalDate> AS_DATE =
			Codec.of(LocalDate.class, Date.class, Date::valueOf, Date::toLocalDate);

	private static Library nativeLibrary(String id) {
		try {
			return Library.over(AbstractTransaction.over(withDates(
					SerializableSource.postgres(id, connect()))));
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Library simulatedLibrary(String id) {
		try {
			return Library.over(AbstractTransaction.over(
					Watermark.over(withDates(SqlFetch.pinned(id, connect())),
							PostgresCommitTest::commitConnection)));
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static SerializableSource withDates(SerializableSource source) {
		return source.withCodec(Schema.loan(null, lvar(), lvar(), lvar(), AS_DATE.arg()))
				.withCodec(Schema.reservation(null, lvar(), lvar(), lvar(), AS_DATE.arg()));
	}

	private static SqlFetch withDates(SqlFetch source) {
		return source.withCodec(Schema.loan(null, lvar(), lvar(), lvar(), AS_DATE.arg()))
				.withCodec(Schema.reservation(null, lvar(), lvar(), lvar(), AS_DATE.arg()));
	}

	private static Connection commitConnection() {
		try {
			return connect();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	// -- the receipts, once per serialization ----------------------------

	private static void seed(Function<String, Library> library) {
		assertThat(library.apply("seed")
				.withTier("standard", 3, 14)
				.withBook("978-0", "SICP", "Abelson")
				.withCopy(1, "978-0")
				.withMember(100, "Ada", "standard")
				.withMember(101, "Alan", "standard")
				.commit().isSuccess()).isTrue();
	}

	private static void commitPersists(Function<String, Library> library) {
		seed(library);
		Library lending = library.apply("lend");
		assertThat(lending.availableCopies("978-0")).containsExactly(1);
		assertThat(lending.checkOut(500, 1, 100, day(10)).get().commit().isSuccess()).isTrue();

		Library after = library.apply("after");
		assertThat(after.availableCopies("978-0")).isEmpty();
		assertThat(after.overdueLoans(day(25))).containsExactly(500);
	}

	private static void doubleCheckoutResolvesToADenial(Function<String, Library> library) {
		seed(library);
		Library ada = library.apply("ada").checkOut(500, 1, 100, day(10)).get();
		Library alan = library.apply("alan").checkOut(501, 1, 101, day(10)).get();

		assertThat(ada.commit().isSuccess()).isTrue();
		Try<?> refused = alan.commit();
		assertThat(refused.isFailure()).isTrue();
		assertThat(refused.getCause()).isInstanceOf(Transaction.Conflict.class);

		assertThat(library.apply("alan-retry").checkOutDenials(101, 1))
				.describedAs("the retry sees the landed loan and answers with the denial")
				.containsExactly("copy not available");
	}

	@Test
	public void aCommitPersistsThroughNativeSerialization() {
		commitPersists(PostgresCommitTest::nativeLibrary);
	}

	@Test
	public void aCommitPersistsThroughSimulatedSerialization() {
		commitPersists(PostgresCommitTest::simulatedLibrary);
	}

	@Test
	public void theDoubleCheckoutResolvesThroughNativeSerialization() {
		doubleCheckoutResolvesToADenial(PostgresCommitTest::nativeLibrary);
	}

	@Test
	public void theDoubleCheckoutResolvesThroughSimulatedSerialization() {
		doubleCheckoutResolvesToADenial(PostgresCommitTest::simulatedLibrary);
	}
}
