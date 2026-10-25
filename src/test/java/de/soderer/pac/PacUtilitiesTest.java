package de.soderer.pac;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.soderer.pac.utilities.Assignment;
import de.soderer.pac.utilities.Condition;
import de.soderer.pac.utilities.Context;
import de.soderer.pac.utilities.ExecutionGuard;
import de.soderer.pac.utilities.Expression;
import de.soderer.pac.utilities.Method;
import de.soderer.pac.utilities.PacScriptParserUtilities;
import de.soderer.pac.utilities.Result;
import de.soderer.pac.utilities.exception.PacScriptExecutionLimitException;
import de.soderer.pac.utilities.exception.ReturnException;

/**
 * Tests of {@link PacScriptParserUtilities}, {@link Context}, {@link ExecutionGuard} and the statement classes used directly.
 */
@SuppressWarnings("static-method")
public class PacUtilitiesTest {
	@TempDir
	Path tempDirectory;

	private static List<String> tokens(final String code) {
		return PacScriptParserUtilities.tokenize(code);
	}

	// ---- Host names ----

	@Test
	public void testGetHostnameFromRequestString() {
		assertEquals("host.example.com", PacScriptParserUtilities.getHostnameFromRequestString("http://user:secret@host.example.com:8080/path?query#fragment"));
		assertEquals("2001:db8::1", PacScriptParserUtilities.getHostnameFromRequestString("https://[2001:db8::1]:8443/path"));
		assertEquals("host", PacScriptParserUtilities.getHostnameFromRequestString(" host:80 "));
		assertEquals("host", PacScriptParserUtilities.getHostnameFromRequestString("host?x=1"));
		assertEquals("host", PacScriptParserUtilities.getHostnameFromRequestString("host#x"));
		// IPv6 addresses without brackets have no port
		assertEquals("::1", PacScriptParserUtilities.getHostnameFromRequestString("::1"));
		assertNull(PacScriptParserUtilities.getHostnameFromRequestString(null));
	}

	// ---- Comments and tokens ----

	@Test
	public void testRemoveComments() {
		assertEquals("a \nb", PacScriptParserUtilities.removeComments("a // comment\nb"));
		assertEquals("a  b", PacScriptParserUtilities.removeComments("a /* multi\nline * comment */ b"));
		assertEquals("a / b", PacScriptParserUtilities.removeComments("a / b"));
		assertEquals("x = \"// no comment /* */\";", PacScriptParserUtilities.removeComments("x = \"// no comment /* */\";"));
		assertEquals("x = \"escaped \\\" // quote\";", PacScriptParserUtilities.removeComments("x = \"escaped \\\" // quote\";"));
		assertEquals("a ", PacScriptParserUtilities.removeComments("a // comment at the end"));
		assertEquals("a/", PacScriptParserUtilities.removeComments("a/"));
	}

	@Test
	public void testTokenize() {
		assertEquals(Arrays.asList("var", "x", "=", "f", "(", "a", ",", "\"text with spaces\"", ")", ";"), tokens("var x=f(a, \"text with spaces\");"));
		assertEquals(Arrays.asList("a", "==", "b", "!=", "c", "<=", "d", ">=", "e"), tokens("a==b!=c<=d>=e"));
		assertEquals(Arrays.asList("a", "+=", "1", ";", "b", "-=", "2", ";", "c", "*=", "3", ";", "d", "/=", "4", ";"), tokens("a+=1;b-=2;c*=3;d/=4;"));
		assertEquals(Arrays.asList("a", "++", ";", "--", "b", ";", "x", "&&", "y", "||", "z", "&", "w", "|", "v"), tokens("a++;--b;x&&y||z&w|v"));
		assertEquals(Arrays.asList("a", "[", "1", "]", "{", "}", "%", "^", "!", "<", ">", "*", "/"), tokens("a[1]{}%^!<>*/"));
		assertEquals(Arrays.asList("\"a\\\"b\""), tokens("\"a\\\"b\""));
		assertEquals(Arrays.asList("x", "y"), tokens("x\r\ny\r"));
		assertEquals(Arrays.asList("trailing"), tokens("trailing"));
		assertEquals(Arrays.asList("if", "(", "a", ")", "{", "b", "}"), tokens("if(a){b}"));
		assertTrue(tokens("  \n\t ").isEmpty());
	}

	@Test
	public void testReplaceAliases() {
		final List<String> tokens = new ArrayList<>(Arrays.asList("not", "x", "nothing"));
		assertSame(tokens, PacScriptParserUtilities.replaceAliases(tokens));
		assertEquals(Arrays.asList("!", "x", "nothing"), tokens);
	}

	// ---- Brackets and operators ----

	@Test
	public void testFindClosingBracketToken() {
		assertEquals(5, PacScriptParserUtilities.findClosingBracketToken(tokens("( a ( b ) )"), 0));
		assertEquals(6, PacScriptParserUtilities.findClosingBracketToken(tokens("{ [ x ] ( ) }"), 0));
		assertEquals(2, PacScriptParserUtilities.findClosingBracketToken(tokens("[ x ] ]"), 0));

		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.findClosingBracketToken(tokens("a ( )"), 0)).getMessage().contains("Not a starting bracket"));
		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.findClosingBracketToken(tokens("( ]"), 0)).getMessage().contains("Unbalanced closing brackets"));
		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.findClosingBracketToken(tokens("[ )"), 0)).getMessage().contains("Unbalanced closing brackets"));
		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.findClosingBracketToken(tokens("{ ]"), 0)).getMessage().contains("Unbalanced closing brackets"));
		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.findClosingBracketToken(tokens("( }"), 0)).getMessage().contains("Unbalanced closing brackets"));
		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.findClosingBracketToken(tokens("( ( )"), 0)).getMessage().contains("Unbalanced brackets starting at index: 0"));
	}

	@Test
	public void testOperatorSearchOutsideOfBrackets() {
		final List<String> tokens = tokens("a + ( b + c ) + d [ e + f ]");
		assertEquals(1, PacScriptParserUtilities.indexOfOperatorOutsideOfBrackets(tokens, List.of("+")));
		assertEquals(7, PacScriptParserUtilities.lastIndexOfOperatorOutsideOfBrackets(tokens, List.of("+")));
		assertEquals(-1, PacScriptParserUtilities.indexOfOperatorOutsideOfBrackets(tokens, List.of("*")));
		assertEquals(-1, PacScriptParserUtilities.lastIndexOfOperatorOutsideOfBrackets(tokens, List.of("*")));
		assertEquals(-1, PacScriptParserUtilities.indexOfOperatorOutsideOfBrackets(tokens("[ a + b ]"), List.of("+")));
	}

	// ---- Text helpers ----

	@Test
	public void testJoinAndIndent() {
		assertEquals("a.b.c", PacScriptParserUtilities.join(new String[] { "a", "b", "c" }, "."));
		assertEquals("", PacScriptParserUtilities.join(new String[0], "."));
		assertEquals("a, b", PacScriptParserUtilities.join(Arrays.asList("a", "b"), ", "));
		assertEquals("", PacScriptParserUtilities.join(Collections.emptyList(), ", "));
		assertEquals("\tline1\n\tline2", PacScriptParserUtilities.indentLines("line1\nline2"));
	}

	// ---- Streams and files ----

	@Test
	public void testStreamHelpers() throws Exception {
		assertNull(PacScriptParserUtilities.toByteArray(null));
		final byte[] data = new byte[10000];
		Arrays.fill(data, (byte) 'x');
		assertArrayEquals(data, PacScriptParserUtilities.toByteArray(new ByteArrayInputStream(data)));
		final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		assertEquals(data.length, PacScriptParserUtilities.copy(new ByteArrayInputStream(data), outputStream));
		assertArrayEquals(data, outputStream.toByteArray());
	}

	@Test
	public void testReadPacDataFromFile() throws Exception {
		final Path pacFile = tempDirectory.resolve("proxy.pac");
		Files.write(pacFile, "line1\r\nline2\rline3\n\u00e4".getBytes(StandardCharsets.UTF_8));
		// Line breaks are normalized, the data is read as UTF-8
		assertEquals("line1\nline2\nline3\n\u00e4", PacScriptParserUtilities.readPacData(pacFile.toUri().toURL()));
	}

	@Test
	public void testReadOversizedPacFile() throws Exception {
		final Path pacFile = tempDirectory.resolve("big.pac");
		Files.write(pacFile, new byte[5 * 1024 * 1024 + 1]);
		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.readPacData(pacFile.toUri().toURL())).getMessage().contains("exceeds maximum allowed size of 5242880 bytes"));
	}

	// ---- Code blocks ----

	@Test
	public void testParseCodeBlockTokens() {
		assertEquals(2, PacScriptParserUtilities.parseCodeBlockTokens(tokens("; var a = 1; ; return a;")).size());
		assertTrue(PacScriptParserUtilities.parseCodeBlockTokens(Collections.emptyList()).isEmpty());
		assertTrue(assertThrows(RuntimeException.class, () -> PacScriptParserUtilities.parseCodeBlockTokens(tokens("unknown;"))).getMessage().contains("Unsupported code found: unknown"));
	}

	// ---- Context ----

	@Test
	public void testContext() {
		final Method method = new Method("m", Collections.emptyList(), tokens("return 1;"));
		final Context context = new Context().withDefinedMethod("m", method).withEnvironmentVariable("v", 1).withConstVariable("c");
		assertSame(method, context.getDefinedMethod("m"));
		assertTrue(context.hasDefinedMethod("m"));
		assertFalse(context.hasDefinedMethod("other"));
		assertEquals(1, context.getEnvironmentVariable("v"));
		assertTrue(context.hasVariable("v"));
		assertFalse(context.hasVariable("other"));
		assertTrue(context.isConstVariable("c"));
		assertFalse(context.isConstVariable("v"));
		assertTrue(context.getExecutionGuard() != null);
	}

	@Test
	public void testSubContextIsIndependent() {
		final Context context = new Context().withEnvironmentVariable("v", 1);
		final Context subContext = context.createSubContext();
		assertEquals(1, subContext.getEnvironmentVariable("v"));
		// The execution limits are shared
		assertSame(context.getExecutionGuard(), subContext.getExecutionGuard());

		subContext.setEnvironmentVariable("v", 2);
		subContext.setEnvironmentVariable("w", 3);
		subContext.setConstVariable("c");
		subContext.setDefinedMethod("m", new Method("m", Collections.emptyList(), Collections.emptyList()));
		assertEquals(1, context.getEnvironmentVariable("v"));
		assertFalse(context.hasVariable("w"));
		assertFalse(context.isConstVariable("c"));
		assertFalse(context.hasDefinedMethod("m"));
	}

	// ---- Execution limits ----

	@Test
	public void testExecutionGuardStepLimit() {
		final ExecutionGuard executionGuard = new ExecutionGuard(2, 60_000, 10);
		executionGuard.checkStep();
		executionGuard.checkStep();
		assertTrue(assertThrows(PacScriptExecutionLimitException.class, executionGuard::checkStep).getMessage().contains("Maximum execution step count exceeded (2)"));
	}

	@Test
	public void testExecutionGuardDurationLimit() throws Exception {
		final ExecutionGuard executionGuard = new ExecutionGuard(1000, 0, 10);
		Thread.sleep(10);
		assertTrue(assertThrows(PacScriptExecutionLimitException.class, executionGuard::checkStep).getMessage().contains("Maximum execution duration exceeded (0 ms)"));
	}

	@Test
	public void testExecutionGuardCallDepthLimit() {
		final ExecutionGuard executionGuard = new ExecutionGuard(1000, 60_000, 2);
		executionGuard.enterMethodCall();
		executionGuard.enterMethodCall();
		executionGuard.leaveMethodCall();
		// Leaving a call allows another call
		executionGuard.enterMethodCall();
		assertTrue(assertThrows(PacScriptExecutionLimitException.class, executionGuard::enterMethodCall).getMessage().contains("Maximum call/recursion depth exceeded (2)"));
	}

	@Test
	public void testMethodLeavesCallDepthAfterException() {
		final ExecutionGuard executionGuard = new ExecutionGuard(1000, 60_000, 1);
		final Context context = new Context(executionGuard);
		final Method failingMethod = new Method("fail", Collections.emptyList(), tokens("return undefinedVariable;"));
		assertThrows(RuntimeException.class, () -> failingMethod.executeMethod(context, Collections.emptyList()));
		// The failed call was left, so another call is allowed
		assertEquals(1, new Method("ok", Collections.emptyList(), tokens("return 1;")).executeMethod(context, Collections.emptyList()));
	}

	// ---- Statements ----

	@Test
	public void testConditionElseBlocks() {
		final Context context = new Context();
		final Condition condition = new Condition(tokens("false"), tokens("return \"if\";"), tokens("return \"else\";"));
		assertEquals("else", assertThrows(ReturnException.class, () -> condition.execute(context)).getReturnValue());

		assertSame(condition, condition.withElseCodeBlockTokens(null));
		assertNull(condition.execute(context));

		assertSame(condition, condition.withElseCodeBlockStatements(Collections.singletonList(new Result(tokens("\"statement\"")))));
		assertEquals("statement", assertThrows(ReturnException.class, () -> condition.execute(context)).getReturnValue());

		condition.setElseCodeBlockTokens(tokens("return \"tokens\";"));
		assertEquals("tokens", assertThrows(ReturnException.class, () -> condition.execute(context)).getReturnValue());
		assertEquals("if (false) {\n\treturn \"if\";\n} else {\n\treturn \"tokens\";\n}", condition.toString());
	}

	@Test
	public void testStatementToString() {
		assertEquals("var x = 1;", new Assignment("x", tokens("1")).toString());
		assertEquals("let x = 1;", new Assignment(Assignment.Scope.LET, "x", tokens("1")).toString());
		assertEquals("const x = 1;", new Assignment(Assignment.Scope.CONST, "x", tokens("1")).toString());
		assertEquals("return;", new Result(null).toString());
		assertEquals("return;", new Result(Collections.emptyList()).toString());
		assertEquals("return f(a, b);", new Result(tokens("f(a, b)")).toString());
		assertEquals("x++", new Expression(tokens("x ++")).toString());
		assertEquals(Arrays.asList("x"), new Expression(tokens("x")).getExpressionTokens());
	}

	@Test
	public void testExpressionsAreParsedOnce() {
		final Context context = new Context().withEnvironmentVariable("x", 5);
		final Expression expression = new Expression(tokens("x * 2 + 1"));
		assertEquals(11, expression.execute(context));
		context.setEnvironmentVariable("x", 1);
		assertEquals(3, expression.execute(context));
	}

	@Test
	public void testExceptions() {
		assertEquals("limit", new PacScriptExecutionLimitException("limit").getMessage());
		assertEquals("value", new ReturnException("value").getReturnValue());
		assertNull(new ReturnException(null).getReturnValue());
	}
}
