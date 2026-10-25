package de.soderer.pac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import de.soderer.pac.utilities.PacScriptMethods;

/**
 * Regression tests for fixed bugs of the PAC script interpreter and the date/time functions.
 * <p>
 * Each test script is the body of "FindProxyForURL(url, host)". Results are returned as text.
 */
@SuppressWarnings("static-method")
public class PacBugfixTest {
	private static final String URL = "http://www.example.com:8080/path?q=1";

	private static String eval(final String body, final String... functions) throws Exception {
		final String pacScript = "function FindProxyForURL(url, host) {\n" + body + "\n}\n" + String.join("\n", functions);
		final List<String> result = new PacScriptParser(pacScript).discoverProxySettings(URL, PacScriptParser.CacheType.None);
		return result == null ? null : String.join(";", result);
	}

	private static void assertError(final String expectedMessagePart, final String body, final String... functions) {
		final Exception exception = assertThrows(Exception.class, () -> eval(body, functions), body);
		assertTrue(exception.getMessage() != null && exception.getMessage().contains(expectedMessagePart), "Expected '" + expectedMessagePart + "' but was: " + exception.getMessage());
	}

	private static Calendar gmtTime(final int year, final int month, final int day, final int hour, final int minute, final int second) {
		final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.clear();
		calendar.set(year, month, day, hour, minute, second);
		return calendar;
	}

	// ---- Parser ----

	@Test
	public void testStatementAfterElseBlockIsComplete() throws Exception {
		// The first token of the statement after a final else block was skipped
		assertEquals("12", eval("var x = 0; if (false) { x = 1; } else { x = 2; } x = x + 10; return x + \"\";"));
		assertEquals("12", eval("var x = 0; if (false) x = 1; else x = 2; x = x + 10; return x + \"\";"));
		assertEquals("13", eval("var x = 0; if (false) { x = 1; } else if (false) { x = 2; } else { x = 3; } x = x + 10; return x + \"\";"));
		assertEquals("B", eval("if (false) { return \"A\"; } else { } return \"B\";"));
	}

	@Test
	public void testMissingSemicolonBeforeBlockEnd() throws Exception {
		assertEquals("DIRECT", eval("return \"DIRECT\""));
		assertEquals("A", eval("if (true) { return \"A\" } return \"B\";"));
	}

	@Test
	public void testSingleQuotedStrings() throws Exception {
		assertEquals("DIRECT", eval("return 'DIRECT';"));
		assertEquals("say \"hi\"", eval("return 'say \"hi\"';"));
		assertEquals("it's", eval("return \"it's\";"));
		assertEquals("yes", eval("if (shExpMatch(host, '*.example.com')) { return 'yes'; } return 'no';"));
		// Comment characters inside single quoted strings are no comments
		assertEquals("a//b", eval("return 'a//b'; // comment"));
	}

	@Test
	public void testCommentEndWithSeveralAsterisks() throws Exception {
		assertEquals("A", eval("/** comment **/ return \"A\";"));
		assertEquals("A", eval("/*** comment ***/\nreturn \"A\";"));
	}

	@Test
	public void testMethodCallStatements() throws Exception {
		// The result of a method call statement does not end the calling function
		assertEquals("B", eval("helper(); return \"B\";", "function helper() { return \"A\"; }"));
		assertEquals("B", eval("if (true) { helper(); } return \"B\";", "function helper() { return \"A\"; }"));
		assertEquals("DIRECT", eval("alert('Checking ' + host); return \"DIRECT\";"));
		assertError("Call of undefined method: unknown", "unknown(); return \"DIRECT\";");
	}

	// ---- Expressions ----

	@Test
	public void testGreaterOrEqualAndLessOrEqualForEqualValues() throws Exception {
		assertEquals("true", eval("return (5 >= 5) + \"\";"));
		assertEquals("true", eval("return (5 <= 5) + \"\";"));
		assertEquals("true", eval("var a = \"x\"; return (a >= a) + \"\";"));
		assertEquals("false", eval("return (4 >= 5) + \"\";"));
	}

	@Test
	public void testNestedMethodCallsAsParameters() throws Exception {
		final String add = "function add(a, b) { return a + b; }";
		assertEquals("6", eval("return add(add(1, 2), 3) + \"\";", add));
		assertEquals("10", eval("return add(add(1, 2), add(3, 4)) + \"\";", add));
		assertEquals("3,2", eval("var a = [add(1, 2), 4]; return a[0] + \",\" + a.length;", add));
		assertEquals("2", eval("var a = [[1, 2], [3]]; return a.length + \"\";"));
	}

	@Test
	public void testEmptyArray() throws Exception {
		assertEquals("0", eval("var a = []; return a.length + \"\";"));
	}

	@Test
	public void testNegativeNumbers() throws Exception {
		assertEquals("-1", eval("return -1 + \"\";"));
		assertEquals("5", eval("return 3 - -2 + \"\";"));
		assertEquals("-6", eval("return (2 * -3) + \"\";"));
		assertEquals("-2.5", eval("var x = -2.5; return x + \"\";"));
		assertEquals("1", eval("var x = 2; return -x + 3 + \"\";"));
		assertEquals("true", eval("return (-1 < 0) + \"\";"));
		assertEquals("4", eval("return +4 + \"\";"));
	}

	@Test
	public void testUnexpectedTokensInExpression() {
		assertError("Unexpected expression when expecting single value", "return \"a\" \"b\";");
		assertError("Unexpected expression when expecting single value", "return isPlainHostName(host) host;");
	}

	@Test
	public void testComparisonWithUndefined() throws Exception {
		assertEquals("ne", eval("if (undefined == 1) { return \"eq\"; } return \"ne\";"));
		assertEquals("ne", eval("if (1 != undefined) { return \"ne\"; } return \"eq\";"));
		assertEquals("eq", eval("if (null == undefined) { return \"eq\"; } return \"ne\";"));
		assertEquals("eq", eval("if (null != null) { return \"ne\"; } return \"eq\";"));
		// Missing parameters are undefined
		assertEquals("missing", eval("return helper();", "function helper(a) { if (a == null) { return \"missing\"; } return a; }"));
	}

	@Test
	public void testStringEscapes() throws Exception {
		assertEquals("a\"b", eval("return \"a\\\"b\";"));
		assertEquals("a'b", eval("return 'a\\'b';"));
		assertEquals("a\\b", eval("return \"a\\\\b\";"));
		assertEquals("A/B", eval("return \"\\u0041\\/\\x42\";"));
		assertEquals("2", eval("var s = \"\\t\\n\"; return s.length + \"\";"));
	}

	@Test
	public void testNumberLiterals() throws Exception {
		// Doubles instead of floats: no precision loss
		assertEquals("0.30000000000000004", eval("return 0.1 + 0.2 + \"\";"));
		assertEquals("2.5", eval("return 1.5 + 1 + \"\";"));
		assertEquals("16", eval("return 0x10 + \"\";"));
		assertEquals("true", eval("return (1e-3 < 0.01) + \"\";"));
		// Java specific number formats are no JavaScript numbers
		assertError("Unexpected code in expression: 1f", "return 1f + \"\";");
		assertError("Unexpected code in expression: 1d", "return 1d + \"\";");
	}

	@Test
	public void testBracketsAndPrecedence() throws Exception {
		assertEquals("9", eval("return (1 + 2) * 3 + \"\";"));
		assertEquals("7", eval("return 1 + (2 * 3) + \"\";"));
		assertEquals("true", eval("return ((1) == (1)) + \"\";"));
		assertEquals("2", eval("return (8 - 2) / (1 + 2) + \"\";"));
	}

	@Test
	public void testToStringKeepsBrackets() throws Exception {
		final String text = new PacScriptParser("function FindProxyForURL(url, host) { var x = 1; var y = (x + 2) * 3; var z = ++x; return y; }").toString();
		assertTrue(text.contains("var y = (x + 2) * 3;"), text);
		assertTrue(text.contains("var z = ++x;"), text);
	}

	@Test
	public void testShortCircuitEvaluation() throws Exception {
		// The right side would fail with an invalid array index
		assertEquals("ok", eval("var a = [1]; if (false && a[5] == 1) { return \"x\"; } return \"ok\";"));
		assertEquals("ok", eval("var a = [1]; if (true || a[5] == 1) { return \"ok\"; } return \"x\";"));
	}

	// ---- Conditions ----

	@Test
	public void testAllStatementsOfBranchAreExecuted() throws Exception {
		assertEquals("5", eval("var x = 0; if (true) { helper(); x = 5; } return x + \"\";", "function helper() { return \"A\"; }"));
		assertEquals("5", eval("var x = 0; if (false) { } else { helper(); x = 5; } return x + \"\";", "function helper() { return \"A\"; }"));
	}

	@Test
	public void testJavaScriptTruthiness() throws Exception {
		assertEquals("yes", eval("if (-1) { return \"yes\"; } return \"no\";"));
		assertEquals("yes", eval("if (0.5) { return \"yes\"; } return \"no\";"));
		assertEquals("yes", eval("if (\" \") { return \"yes\"; } return \"no\";"));
		assertEquals("no", eval("if (\"\") { return \"yes\"; } return \"no\";"));
		assertEquals("no", eval("if (0) { return \"yes\"; } return \"no\";"));
		assertEquals("no", eval("if (undefined) { return \"yes\"; } return \"no\";"));
		assertEquals("3", eval("var i = 3; var n = 0; while (i) { i--; n++; } return n + \"\";"));
	}

	// ---- Date and time ----

	@Test
	public void testDateRangeAt() {
		final Calendar now = gmtTime(2026, Calendar.JANUARY, 15, 12, 0, 0);
		assertTrue(PacScriptMethods.dateRangeAt(now, 15, "GMT", null, null, null, null, null));
		assertFalse(PacScriptMethods.dateRangeAt(now, 16, "GMT", null, null, null, null, null));
		assertTrue(PacScriptMethods.dateRangeAt(now, 1, 15, "GMT", null, null, null, null));
		assertFalse(PacScriptMethods.dateRangeAt(now, 16, 31, "GMT", null, null, null, null));
		assertTrue(PacScriptMethods.dateRangeAt(now, "JAN", "GMT", null, null, null, null, null));
		assertFalse(PacScriptMethods.dateRangeAt(now, "FEB", "GMT", null, null, null, null, null));
		assertTrue(PacScriptMethods.dateRangeAt(now, 2026, "GMT", null, null, null, null, null));
		assertTrue(PacScriptMethods.dateRangeAt(now, 2025, 2027, "GMT", null, null, null, null));
		assertFalse(PacScriptMethods.dateRangeAt(now, 2027, 2025, "GMT", null, null, null, null));
		assertTrue(PacScriptMethods.dateRangeAt(now, 1, "JAN", 2026, 31, "JAN", 2026, "GMT"));
		assertFalse(PacScriptMethods.dateRangeAt(now, 16, "JAN", 2026, 31, "DEC", 2026, "GMT"));
		assertTrue(PacScriptMethods.dateRangeAt(now, "DEC", 2025, "FEB", 2026, "GMT", null, null));
		assertFalse(PacScriptMethods.dateRangeAt(now, "FEB", 2026, "MAR", 2026, "GMT", null, null));
	}

	@Test
	public void testDateRangeAtWrapping() {
		final Calendar january = gmtTime(2026, Calendar.JANUARY, 15, 12, 0, 0);
		// "DEC" to "JAN" includes January
		assertTrue(PacScriptMethods.dateRangeAt(january, "DEC", "JAN", "GMT", null, null, null, null));
		assertFalse(PacScriptMethods.dateRangeAt(january, "FEB", "NOV", "GMT", null, null, null, null));
		// Day ranges over the end of the month
		assertTrue(PacScriptMethods.dateRangeAt(january, 10, 5, "GMT", null, null, null, null));
		assertFalse(PacScriptMethods.dateRangeAt(january, 20, 10, "GMT", null, null, null, null));
		// Day and month ranges over the end of the year
		assertTrue(PacScriptMethods.dateRangeAt(january, 1, "DEC", 15, "JAN", "GMT", null, null));
		assertFalse(PacScriptMethods.dateRangeAt(january, 1, "DEC", 14, "JAN", "GMT", null, null));
	}

	@Test
	public void testDateRangeAtTimeZone() {
		// 23:30 GMT on 31st December is already 1st January in Berlin
		final Calendar now = gmtTime(2026, Calendar.DECEMBER, 31, 23, 30, 0);
		final TimeZone defaultTimeZone = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
			assertTrue(PacScriptMethods.dateRangeAt(now, 1, "JAN", null, null, null, null, null));
			assertTrue(PacScriptMethods.dateRangeAt(now, 31, "DEC", "GMT", null, null, null, null));
		} finally {
			TimeZone.setDefault(defaultTimeZone);
		}
	}

	@Test
	public void testDateAndTimeRangeOnDaylightSavingTimeChange() {
		// 01:30 GMT on 25th October 2026 is 02:30 CET, after the clocks were set back from 03:00 CEST
		final Calendar now = gmtTime(2026, Calendar.OCTOBER, 25, 1, 30, 0);
		final TimeZone defaultTimeZone = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
			assertTrue(PacScriptMethods.dateRangeAt(now, 25, null, null, null, null, null, null));
			assertTrue(PacScriptMethods.dateRangeAt(now, 25, "OCT", null, null, null, null, null));
			assertTrue(PacScriptMethods.timeRangeAt(now, 2, null, null, null, null, null, null));
			assertTrue(PacScriptMethods.timeRangeAt(now, 2, 30, 0, 2, 30, 0, null));
			assertFalse(PacScriptMethods.timeRangeAt(now, 3, null, null, null, null, null, null));
		} finally {
			TimeZone.setDefault(defaultTimeZone);
		}
	}

	@Test
	public void testTimeRangeAt() {
		final Calendar now = gmtTime(2026, Calendar.MARCH, 1, 23, 30, 0);
		assertTrue(PacScriptMethods.timeRangeAt(now, 23, "GMT", null, null, null, null, null));
		assertFalse(PacScriptMethods.timeRangeAt(now, 0, "GMT", null, null, null, null, null));
		// Range over midnight
		assertTrue(PacScriptMethods.timeRangeAt(now, 22, 2, "GMT", null, null, null, null));
		assertFalse(PacScriptMethods.timeRangeAt(now, 0, 22, "GMT", null, null, null, null));
		assertTrue(PacScriptMethods.timeRangeAt(now, 23, 30, 0, 23, 30, 0, "GMT"));
		assertFalse(PacScriptMethods.timeRangeAt(now, 23, 31, 23, 59, "GMT", null, null));
		assertFalse(PacScriptMethods.timeRangeAt(now, 23, 0, 23, 29, "GMT", null, null));
		assertTrue(PacScriptMethods.timeRangeAt(now, 23, 0, 23, 30, "GMT", null, null));
		assertError("Invalid numeric parameter: x", "return timeRange(\"x\", 1) + \"\";");
	}
}
