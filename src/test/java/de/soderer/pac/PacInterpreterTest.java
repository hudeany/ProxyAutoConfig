package de.soderer.pac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.soderer.pac.utilities.exception.PacScriptExecutionLimitException;

/**
 * Tests of the PAC script interpreter: expressions and operators, arrays, conditions, loops, method calls, variable declarations,
 * comments and the error messages for unsupported or invalid code.
 * <p>
 * Each test script is the body of "FindProxyForURL(url, host)", which is called for the URL "http://www.example.com:8080/path?q=1".
 * Results are returned as text, several values are separated by "," (";" separates proxy settings).
 */
@SuppressWarnings("static-method")
public class PacInterpreterTest {
	private static final String URL = "http://www.example.com:8080/path?q=1";

	private static String script(final String body, final String... functions) {
		return "function FindProxyForURL(url, host) {\n" + body + "\n}\n" + String.join("\n", functions);
	}

	/**
	 * Executes the body of FindProxyForURL and returns the result joined by ";" or null
	 */
	private static String eval(final String body, final String... functions) throws Exception {
		final List<String> result = new PacScriptParser(script(body, functions)).discoverProxySettings(URL, PacScriptParser.CacheType.None);
		return result == null ? null : String.join(";", result);
	}

	private static void assertError(final String expectedMessagePart, final String body, final String... functions) {
		final Exception exception = assertThrows(Exception.class, () -> eval(body, functions), body);
		assertTrue(exception.getMessage() != null && exception.getMessage().contains(expectedMessagePart), "Expected '" + expectedMessagePart + "' but was: " + exception.getMessage());
	}

	// ---- Parameters ----

	@Test
	public void testFindProxyForUrlParameters() throws Exception {
		assertEquals(URL, eval("return url;"));
		assertEquals("www.example.com", eval("return host;"));
	}

	// ---- Arithmetic ----

	@Test
	public void testIntegerArithmetic() throws Exception {
		assertEquals("7", eval("return (3 + 4) + \"\";"));
		assertEquals("-1", eval("return (3 - 4) + \"\";"));
		assertEquals("12", eval("return (3 * 4) + \"\";"));
		assertEquals("3", eval("return (7 / 2) + \"\";"));
		assertEquals("1", eval("return (7 % 3) + \"\";"));
	}

	@Test
	public void testFloatingPointArithmetic() throws Exception {
		assertEquals("2.5", eval("return (1.5 + 1) + \"\";"));
		assertEquals("0.5", eval("return (1 - 0.5) + \"\";"));
		assertEquals("5.0", eval("return (2.5 * 2) + \"\";"));
		assertEquals("3.5", eval("return (7 / 2.0) + \"\";"));
		assertEquals("1.5", eval("return (7.5 % 2) + \"\";"));
		assertEquals("1000.0", eval("return 1e3 + \"\";"));
	}

	@Test
	public void testOperatorPrecedenceAndAssociativity() throws Exception {
		assertEquals("7", eval("return (1 + 2 * 3) + \"\";"));
		assertEquals("9", eval("return ((1 + 2) * 3) + \"\";"));
		assertEquals("14", eval("return (2 * (3 + 4)) + \"\";"));
		// Operators of the same level are evaluated from left to right
		assertEquals("5", eval("return (10 - 2 - 3) + \"\";"));
		assertEquals("5", eval("return (100 / 10 / 2) + \"\";"));
		assertEquals("3x", eval("return 1 + 2 + \"x\";"));
		assertEquals("x12", eval("return \"x\" + 1 + 2;"));
		assertEquals("true", eval("return (1 + 2 * 3 == 7) + \"\";"));
		assertEquals("true", eval("return (true || false && false) + \"\";"));
	}

	@Test
	public void testStringConcatenation() throws Exception {
		assertEquals("ab", eval("return \"a\" + \"b\";"));
		assertEquals("a1", eval("return \"a\" + 1;"));
		assertEquals("1a", eval("return 1 + \"a\";"));
		assertEquals("truex", eval("return true + \"x\";"));
		assertEquals("PROXY www.example.com:3128", eval("return \"PROXY \" + host + \":3128\";"));
	}

	@Test
	public void testUnsupportedArithmeticOperands() {
		assertError("Unsupported parameters for operator: +", "return (1 + true) + \"\";");
		assertError("Unsupported parameters for operator: -", "return \"a\" - 1;");
		assertError("Unsupported parameters for operator: *", "return \"a\" * 2;");
		assertError("Unsupported parameters for operator: /", "return \"a\" / 2;");
		assertError("Unsupported parameters for operator: %", "return \"a\" % 2;");
	}

	// ---- Comparisons ----

	@Test
	public void testEquality() throws Exception {
		assertEquals("true", eval("return (\"abc\" == \"abc\") + \"\";"));
		assertEquals("false", eval("return (\"abc\" == \"abd\") + \"\";"));
		assertEquals("true", eval("return (1 == 1) + \"\";"));
		assertEquals("false", eval("return (1 == 2) + \"\";"));
		// Integer and floating point numbers are compared by value
		assertEquals("true", eval("return (1 == 1.0) + \"\";"));
		assertEquals("true", eval("return (host == \"www.example.com\") + \"\";"));
		assertEquals("true", eval("var x = \"value\"; return (x == x) + \"\";"));

		assertEquals("true", eval("return (\"abc\" != \"abd\") + \"\";"));
		assertEquals("false", eval("return (\"abc\" != \"abc\") + \"\";"));
		assertEquals("true", eval("return (1 != 2) + \"\";"));
		assertEquals("false", eval("return (1.5 != 1.5) + \"\";"));
		assertEquals("true", eval("return (1.5 != 2) + \"\";"));
		assertEquals("false", eval("var x = \"value\"; return (x != x) + \"\";"));
	}

	@Test
	public void testUndefinedValuesAreEqual() throws Exception {
		// Missing method parameters are undefined
		assertEquals("true", eval("return check();", "function check(a, b) { return (a == b) + \"\"; }"));
	}

	@Test
	public void testRelationalOperators() throws Exception {
		assertEquals("true", eval("return (3 > 2) + \"\";"));
		assertEquals("false", eval("return (2 > 3) + \"\";"));
		assertEquals("true", eval("return (2.5 > 2) + \"\";"));
		assertEquals("true", eval("return (2 < 3) + \"\";"));
		assertEquals("false", eval("return (2.5 < 2) + \"\";"));
		assertEquals("true", eval("return (3 >= 2) + \"\";"));
		assertEquals("false", eval("return (2 >= 3) + \"\";"));
		assertEquals("true", eval("return (2.5 >= 2) + \"\";"));
		assertEquals("true", eval("return (2 <= 3) + \"\";"));
		assertEquals("false", eval("return (3 <= 2) + \"\";"));
		assertEquals("true", eval("return (2.5 <= 3) + \"\";"));
		assertEquals("false", eval("var x = 4; return (x > x) + \"\";"));
		assertEquals("false", eval("var x = 4; return (x < x) + \"\";"));
	}

	@Test
	public void testUnsupportedComparisonOperands() {
		assertError("Unsupported parameters for operator: ==", "return (\"a\" == 1) + \"\";");
		assertError("Unsupported parameters for operator: !=", "return (\"a\" != 1) + \"\";");
		assertError("Unsupported parameters for operator: >", "return (\"b\" > \"a\") + \"\";");
		assertError("Unsupported parameters for operator: <", "return (\"b\" < \"a\") + \"\";");
		assertError("Unsupported parameters for operator: >=", "return (\"b\" >= \"a\") + \"\";");
		assertError("Unsupported parameters for operator: <=", "return (\"b\" <= \"a\") + \"\";");
	}

	// ---- Logical operators ----

	@Test
	public void testLogicalOperators() throws Exception {
		assertEquals("false", eval("return (true && false) + \"\";"));
		assertEquals("true", eval("return (true && true) + \"\";"));
		assertEquals("true", eval("return (true || false) + \"\";"));
		assertEquals("false", eval("return (false || false) + \"\";"));
		assertEquals("true", eval("return (true & true) + \"\";"));
		assertEquals("false", eval("return (true & false) + \"\";"));
		assertEquals("true", eval("return (false | true) + \"\";"));
		assertEquals("false", eval("return (!true) + \"\";"));
		assertEquals("true", eval("return (not false) + \"\";"));
		assertEquals("true", eval("return (!(1 > 2)) + \"\";"));
		assertEquals("true", eval("return (isPlainHostName(\"intranet\") && !isPlainHostName(host)) + \"\";"));
	}

	@Test
	public void testUnsupportedLogicalOperands() {
		assertError("Unsupported parameters for operator: &&", "return (1 && true) + \"\";");
		assertError("Unsupported parameters for operator: ||", "return (\"a\" || true) + \"\";");
		assertError("Unsupported parameters for operator: &", "return (1 & 2) + \"\";");
		assertError("Unsupported parameters for operator: |", "return (1 | 2) + \"\";");
		assertError("Unsupported operator: !", "return (!\"a\") + \"\";");
	}

	// ---- Increment and decrement ----

	@Test
	public void testIncrementAndDecrementExpressions() throws Exception {
		// Postfix returns the old value, prefix the new value, both change the variable
		assertEquals("6,5", eval("var x = 5; var y = x++; return x + \",\" + y;"));
		assertEquals("6,6", eval("var x = 5; var y = ++x; return x + \",\" + y;"));
		assertEquals("4,5", eval("var x = 5; var y = x--; return x + \",\" + y;"));
		assertEquals("4,4", eval("var x = 5; var y = --x; return x + \",\" + y;"));
		// Literals are incremented without side effects
		assertEquals("4", eval("return ++3 + \"\";"));
		assertEquals("2", eval("return --3 + \"\";"));
	}

	@Test
	public void testIncrementAndDecrementStatements() throws Exception {
		assertEquals("2", eval("var x = 0; x++; ++x; return x + \"\";"));
		assertEquals("-2", eval("var x = 0; x--; --x; return x + \"\";"));
	}

	@Test
	public void testUnsupportedIncrementOperands() {
		assertError("Unsupported operator: ++", "return ++\"a\";");
		assertError("Unsupported operator: --", "return --\"a\";");
		assertError("Unsupported operator: ++", "var x = \"a\"; return x++;");
		assertError("Unsupported operator: --", "var x = \"a\"; return x--;");
		assertError("Unsupported expression with unary operator found: !", "return 1 ! 2;");
	}

	// ---- Compound assignments ----

	@Test
	public void testCompoundAssignments() throws Exception {
		assertEquals("13", eval("var x = 10; x += 1 + 2; return x + \"\";"));
		assertEquals("7", eval("var x = 10; x -= 1 + 2; return x + \"\";"));
		assertEquals("30", eval("var x = 10; x *= 1 + 2; return x + \"\";"));
		assertEquals("2", eval("var x = 10; x /= 2 + 3; return x + \"\";"));
		assertEquals("ab", eval("var x = \"a\"; x += \"b\"; return x;"));
	}

	// ---- Variable declarations ----

	@Test
	public void testVariableDeclarations() throws Exception {
		assertEquals("2", eval("var x = 1; var x = 2; return x + \"\";"));
		assertEquals("3", eval("let x = 1; x = 3; return x + \"\";"));
		assertEquals("1", eval("const x = 1; return x + \"\";"));
		assertEquals("4", eval("x = 4; return x + \"\";"));
	}

	@Test
	public void testInvalidVariableDeclarations() {
		assertError("Multiple declaration of variablename by 'let' keyword: x", "let x = 1; let x = 2; return \"\";");
		assertError("Multiple declaration of variablename by 'let' keyword: x", "var x = 1; let x = 2; return \"\";");
		assertError("Multiple declaration of variablename by 'const' keyword: x", "const x = 1; const x = 2; return \"\";");
		assertError("Assignment to constant after declaration: x", "const x = 1; x += 1; return \"\";");
		assertError("Unexpected code at token index", "var x 1; return \"\";");
		assertError("Unexpected code at token index", "let x 1; return \"\";");
		assertError("Unexpected code at token index", "const x 1; return \"\";");
	}

	@Test
	public void testInvalidStatements() {
		assertError("Unsupported code found: foo", "foo; return \"\";");
		assertError("Unsupported code found: ++", "++;");
		assertError("Unsupported code found: --", "--;");
		assertError("Unexpected code at token index", "var x = 1; ++x y; return \"\";");
		assertError("Unexpected code at token index", "var x = 1; --x y; return \"\";");
		assertError("Unexpected code at token index", "var x = 1; x++ y; return \"\";");
		assertError("Unexpected code at token index", "var x = 1; x-- y; return \"\";");
	}

	// ---- Values and expressions ----

	@Test
	public void testInvalidExpressions() {
		assertError("Unexpected code in expression: foo", "return foo;");
		assertError("Unexpected expression when expecting single value", "return foo bar;");
		assertError("Unsupported empty expression", "return ();");
		assertError("Call of undefined method: undefinedMethod", "return undefinedMethod(1);");
	}

	@Test
	public void testBooleanLiterals() throws Exception {
		assertEquals("true", eval("return true + \"\";"));
		assertEquals("false", eval("return false + \"\";"));
	}

	@Test
	public void testNonStringResultsAreNoProxySettings() throws Exception {
		assertNull(eval("return 1;"));
		assertNull(eval("return true;"));
		assertNull(eval("return [\"DIRECT\"];"));
		assertNull(eval("return;"));
		// Without return statement the result is undefined
		assertNull(eval("var x = 1;"));
	}

	// ---- Arrays ----

	@Test
	public void testArrays() throws Exception {
		assertEquals("3,b,3", eval("var a = [1, \"b\", 1 + 2]; return a.length + \",\" + a[1] + \",\" + a[a.length - 1];"));
		assertEquals("2", eval("var a = [1, 2]; return a[index()] + \"\";", "function index() { return 1; }"));
	}

	@Test
	public void testInvalidArrayAccess() {
		assertError("Invalid array index: 5", "var a = [1, 2]; return a[5] + \"\";");
		assertError("Invalid array index value: -1", "var a = [1, 2]; return a[0 - 1] + \"\";");
		assertError("Invalid array index value: x", "var a = [1, 2]; return a[\"x\"] + \"\";");
		assertError("Invalid array reference: s", "var s = \"text\"; return s[0];");
		assertError("Array Index call for undefined variable: u", "return u[0];");
		assertError("Variable for length is not an array: n.length", "var n = 5; return n.length + \"\";");
		assertError("Undefined variable name in expression: u.length", "return u.length + \"\";");
		assertError("Unsupported parameters for operator: +", "return [1, 2] + 1;");
		assertError("Unsupported expression with operator for array found: [", "return [1, 2][0];");
	}

	// ---- Conditions ----

	@Test
	public void testIfElseChains() throws Exception {
		final String body = "if (host == \"a\") { return \"A\"; } else if (host == \"b\") return \"B\"; else if (host == \"c\") { return \"C\"; } else return \"other\";";
		final PacScriptParser parser = new PacScriptParser(script(body));
		assertEquals(List.of("A"), parser.discoverProxySettings("http://a/", PacScriptParser.CacheType.None));
		assertEquals(List.of("B"), parser.discoverProxySettings("http://b/", PacScriptParser.CacheType.None));
		assertEquals(List.of("C"), parser.discoverProxySettings("http://c/", PacScriptParser.CacheType.None));
		assertEquals(List.of("other"), parser.discoverProxySettings("http://d/", PacScriptParser.CacheType.None));
	}

	@Test
	public void testIfWithoutElseContinues() throws Exception {
		assertEquals("after", eval("if (false) { return \"inside\"; } return \"after\";"));
		assertEquals("after", eval("if (false) return \"inside\"; return \"after\";"));
		assertEquals("set", eval("var x = \"unset\"; if (true) { x = \"set\"; } return x;"));
		assertEquals("else", eval("var x = \"\"; if (false) { x = \"if\"; return x; } else { x = \"else\"; return x; }"));
		assertEquals("nested", eval("if (true) { if (1 < 2) { return \"nested\"; } } return \"outer\";"));
	}

	@Test
	public void testTruthyConditions() throws Exception {
		assertEquals("yes", eval("if (1) { return \"yes\"; } return \"no\";"));
		assertEquals("else", eval("if (0) { return \"yes\"; } else { return \"else\"; }"));
		assertEquals("no", eval("if (0) { return \"yes\"; } return \"no\";"));
		assertEquals("yes", eval("if (\"text\") { return \"yes\"; } return \"no\";"));
		// A blank is a non-empty string and therefore true, like in JavaScript
		assertEquals("yes", eval("if (\" \") { return \"yes\"; } else { return \"else\"; }"));
		assertEquals("no", eval("if (\"\") { return \"yes\"; } return \"no\";"));
		// Any other value, e.g. an array, is true
		assertEquals("yes", eval("var a = [1]; if (a) { return \"yes\"; } return \"no\";"));
		// Undefined values are false
		assertEquals("else,none", eval("return check() + \",\" + checkWithoutElse();",
				"function check(a) { if (a) { return \"if\"; } else { return \"else\"; } }",
				"function checkWithoutElse(a) { if (a) { return \"if\"; } return \"none\"; }"));
	}

	@Test
	public void testConditionsWithOtherStatements() throws Exception {
		assertEquals("1", eval("var x = 0; if (5) { x = 1; } return x + \"\";"));
		assertEquals("2", eval("var x = 0; if (0) { x = 1; } else { x = 2; return x + \"\"; }"));
		assertEquals("1", eval("var x = 0; if (\"text\") { x = 1; } return x + \"\";"));
		assertEquals("2", eval("var x = 0; if (\"\") { x = 1; } else { x = 2; return x + \"\"; }"));
		assertEquals("1", eval("var a = [1]; var x = 0; if (a) { x = 1; } return x + \"\";"));
		assertEquals("2", eval("return check();", "function check(a) { var x = 0; if (a) { x = 1; } else { x = 2; return x + \"\"; } }"));
		assertEquals("0", eval("var x = 0; if (0) { x = 1; } return x + \"\";"));
	}

	@Test
	public void testInvalidConditions() {
		assertError("Unexpected code at token index", "if true { return \"x\"; }");
		assertError("Unexpected code at token index", "if (false) { return \"x\"; } else if true { return \"y\"; }");
	}

	// ---- Loops ----

	@Test
	public void testForLoops() throws Exception {
		assertEquals("10", eval("var sum = 0; for (var i = 0; i < 5; i++) { sum += i; } return sum + \"\";"));
		assertEquals("3", eval("var count = 0; for (let i = 0; i < 6; i += 2) count++; return count + \"\";"));
		assertEquals("0", eval("var count = 0; for (var i = 0; i < 0; i++) { count++; } return count + \"\";"));
		assertEquals("2", eval("for (var i = 0; i < 10; i++) { if (i == 2) { return i + \"\"; } } return \"none\";"));
		assertEquals("4", eval("var count = 0; for (var i = 0; i < 10; i++) { if (i == 4) { break; } count++; } return count + \"\";"));
		assertEquals("5", eval("var count = 0; for (var i = 0; i < 10; i++) { if (i % 2 == 0) { continue; } count++; } return count + \"\";"));
	}

	@Test
	public void testWhileLoops() throws Exception {
		assertEquals("3", eval("var i = 0; while (i < 3) i++; return i + \"\";"));
		assertEquals("3", eval("var i = 0; while (i < 3) { i++; } return i + \"\";"));
		assertEquals("found", eval("var i = 0; while (true) { i++; if (i == 5) { return \"found\"; } } return \"none\";"));
		assertEquals("0", eval("var i = 0; while (false) { i++; } return i + \"\";"));
	}

	@Test
	public void testInvalidLoops() {
		assertError("Unsupported loop init expression with const", "for (const i = 0; i < 3; i++) { } return \"\";");
		assertError("Unsupported loop init expression", "var i = 0; for (i = 0; i < 3; i++) { } return \"\";");
		assertError("Unsupported loop empty init expression", "var i = 0; for (; i < 3; i++) { } return \"\";");
		assertError("Unsupported loop empty condition expression", "for (var i = 0;; i++) { } return \"\";");
		assertError("Unsupported loop empty condition expression", "for (var i = 0; i < 3) { } return \"\";");
		assertError("Unexpected code in loop init: 0", "for (var i 0; i < 3; i++) { } return \"\";");
		assertError("Unsupported loop step expression", "for (var i = 0; i < 3; i++; i++) { } return \"\";");
		// Loop conditions are truthy values like in JavaScript, so these are infinite loops
		assertError("Maximum execution step count exceeded", "for (var i = 0; 1; i++) { } return \"\";");
		assertError("Maximum execution step count exceeded", "while (1) { } return \"\";");
		assertError("Unsupported loop empty condition expression", "while () { } return \"\";");
		assertError("Unexpected code at token index", "for var i = 0; { } return \"\";");
		assertError("Unexpected code at token index", "while true { } return \"\";");
	}

	@Test
	public void testInfiniteLoopIsStopped() {
		final Exception exception = assertThrows(PacScriptExecutionLimitException.class, () -> eval("while (true) { } return \"x\";"));
		assertTrue(exception.getMessage().contains("Maximum execution step count exceeded"));
	}

	// ---- Methods ----

	@Test
	public void testMethodCalls() throws Exception {
		assertEquals("120", eval("return factorial(5) + \"\";", "function factorial(n) { if (n <= 1) { return 1; } return n * factorial(n - 1); }"));
		// Missing parameters are undefined, additional parameters are ignored
		assertEquals("a:true", eval("return describe(\"a\") + \":\" + (missing(1, 2, 3) == missing(4));",
				"function describe(first, second) { if (second == second) { return first; } return \"x\"; }",
				"function missing() { return 1; }"));
		assertEquals("3", eval("var b = add1(1); return add(1, b) + \"\";", "function add(a, b) { return a + b; }", "function add1(a) { return a + 1; }"));
		// A method without return statement returns undefined
		assertEquals("true", eval("return (nothing() == nothing()) + \"\";", "function nothing() { var x = 1; }"));
		assertEquals("true", eval("return (empty() == empty()) + \"\";", "function empty() { return; }"));
	}

	@Test
	public void testMethodsDoNotChangeCallerVariables() throws Exception {
		assertEquals("1,2", eval("var x = 1; var y = change(); return x + \",\" + y;", "function change() { x = 2; return x; }"));
	}

	@Test
	public void testEndlessRecursionIsStopped() {
		final Exception exception = assertThrows(PacScriptExecutionLimitException.class, () -> eval("return recurse(1);", "function recurse(n) { return recurse(n + 1); }"));
		assertTrue(exception.getMessage().contains("Maximum call/recursion depth exceeded"));
	}

	// ---- Comments ----

	@Test
	public void testComments() throws Exception {
		assertEquals("DIRECT", eval("// single line comment\nreturn \"DIRECT\"; // trailing comment"));
		assertEquals("DIRECT", eval("/* multi\n line * comment ** */ return /* inline */ \"DIRECT\";"));
		// Division is no comment
		assertEquals("2", eval("var a = 8; var b = 4; return a / b + \"\";"));
		// Comment markers in strings are kept
		assertEquals("http://proxy/*x*/", eval("return \"http://proxy/*x*/\";"));
	}
}
