package de.soderer.pac;

import java.net.Proxy;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import de.soderer.pac.utilities.PacScriptParserUtilities;

public class PacTest {
	@SuppressWarnings("static-method")
	@Test
	public void test1() {
		try {
			@SuppressWarnings("unused")
			final String pacUrlString = PacScriptParser.findPacFileUrlByWpad();
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void test2() throws Exception {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_Basic.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);
			final List<Proxy> proxies = pacScriptParser.discoverProxy("https://example.com");
			Assert.assertEquals(1, proxies.size());
			Assert.assertEquals("HTTP @ proxy/<unresolved>:80", proxies.get(0).toString());
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void test3() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_NoBlockBracket.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);
			final List<Proxy> proxies = pacScriptParser.discoverProxy("https://example.com");
			Assert.assertEquals(1, proxies.size());
			Assert.assertEquals("HTTP @ proxy/<unresolved>:80", proxies.get(0).toString());
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void test4() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_Methods.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);
			final List<Proxy> proxies = pacScriptParser.discoverProxy("https://example.com");
			Assert.assertEquals(1, proxies.size());
			Assert.assertEquals("HTTP @ /10.0.0.1:8080", proxies.get(0).toString());
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
}
