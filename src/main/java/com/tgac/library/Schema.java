package com.tgac.library;

// ABOUTME: The library's relational schema — every base relation the domain
// ABOUTME: stores as facts; derived relations live in Rules.

import com.tgac.pldb.relations.Property;
import com.tgac.pldb.relations.Relations;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Schema {

	public static final Property<String> isbn = Property.<String>of("isbn").indexed();
	public static final Property<String> title = Property.<String>of("title");
	public static final Property<String> author = Property.<String>of("author").indexed();

	/** A title in the catalog. */
	public static final Relations._3<String, String, String> book =
			Relations.relation("book", isbn, title, author);

	public static final Property<Integer> copyId = Property.<Integer>of("copyId").indexed();

	/** A physical copy on the shelves. */
	public static final Relations._2<Integer, String> copy =
			Relations.relation("copy", copyId, isbn);

	public static final Property<Integer> memberId = Property.<Integer>of("memberId").indexed();
	public static final Property<String> memberName = Property.<String>of("name");

	/** A registered member. */
	public static final Relations._2<Integer, String> member =
			Relations.relation("member", memberId, memberName);

	public static final Property<Integer> loanId = Property.<Integer>of("loanId").indexed();
	public static final Property<Integer> dueDay = Property.<Integer>of("dueDay");

	/** A checkout event; stays forever, returns are separate events. */
	public static final Relations._4<Integer, Integer, Integer, Integer> loan =
			Relations.relation("loan", loanId, copyId, memberId, dueDay);

	/** A return event closing a loan. */
	public static final Relations._1<Integer> returned =
			Relations.relation("returned", loanId);

	public static final Property<Integer> resId = Property.<Integer>of("resId").indexed();
	public static final Property<Integer> day = Property.<Integer>of("day");

	/** A hold on a title; stays forever, cancellation and fulfillment are separate events. */
	public static final Relations._4<Integer, String, Integer, Integer> reservation =
			Relations.relation("reservation", resId, isbn, memberId, day);

	/** A cancellation event closing a reservation. */
	public static final Relations._1<Integer> cancelled =
			Relations.relation("cancelled", resId);

	/** A fulfillment event closing a reservation — its member checked the title out. */
	public static final Relations._1<Integer> fulfilled =
			Relations.relation("fulfilled", resId);
}
