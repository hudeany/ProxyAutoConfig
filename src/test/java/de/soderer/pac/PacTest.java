package de.soderer.pac;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import de.soderer.pac.utilities.PacScriptParserUtilities;

@SuppressWarnings("static-method")
public class PacTest {
	@Test
	public void testWpad() {
		try {
			@SuppressWarnings("unused")
			final String pacUrlString = PacScriptParser.findPacFileUrlByWpad();
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testBasic() throws Exception {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_Basic.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);
			final List<String> proxySettings = pacScriptParser.discoverProxySettings("https://example.com");
			Assert.assertEquals(1, proxySettings.size());
			Assert.assertEquals("PROXY proxy:80", proxySettings.get(0));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testNoBlockBracket() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_NoBlockBracket.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);
			final List<String> proxySettings = pacScriptParser.discoverProxySettings("https://example.com");
			Assert.assertEquals(1, proxySettings.size());
			Assert.assertEquals("PROXY proxy:80", proxySettings.get(0));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testMethods() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_Methods.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);
			final List<String> proxySettings = pacScriptParser.discoverProxySettings("https://example.com");
			Assert.assertEquals(1, proxySettings.size());
			Assert.assertEquals("PROXY 10.0.0.1:8080", proxySettings.get(0));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testConditions() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_Conditions.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);

			final List<String> proxyExample = pacScriptParser.discoverProxySettings("https://checkHostname");
			Assert.assertEquals(1, proxyExample.size());
			Assert.assertEquals("check hostname", proxyExample.get(0));

			final List<String> proxyOther = pacScriptParser.discoverProxySettings("https://otherHostname");
			Assert.assertEquals(1, proxyOther.size());
			Assert.assertEquals("other hostname", proxyOther.get(0));

			final List<String> proxySimple = pacScriptParser.discoverProxySettings("https://unknownHostname");
			Assert.assertEquals(1, proxySimple.size());
			Assert.assertEquals("no match", proxySimple.get(0));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testAssignmentsAndOperators() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_AssignmentsAndOperators.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);

			final List<String> proxySettings = pacScriptParser.discoverProxySettings("https://example.com");
			Assert.assertEquals("19", proxySettings.get(0));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testNotOperator() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_NotOperator.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);

			final List<String> proxySettings = pacScriptParser.discoverProxySettings("https://example.com");
			Assert.assertEquals("true", proxySettings.get(0));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testArray() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_Array.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);

			final List<String> proxySettings = pacScriptParser.discoverProxySettings("https://example.com");
			Assert.assertEquals("DIRECT", proxySettings.get(0));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
}
