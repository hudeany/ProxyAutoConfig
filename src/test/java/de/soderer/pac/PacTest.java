package de.soderer.pac;

import java.net.Proxy;
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
			final List<Proxy> proxies = pacScriptParser.discoverProxy("https://example.com");
			Assert.assertEquals(1, proxies.size());
			Assert.assertEquals("HTTP @ proxy/<unresolved>:80", proxies.get(0).toString());
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
			final List<Proxy> proxies = pacScriptParser.discoverProxy("https://example.com");
			Assert.assertEquals(1, proxies.size());
			Assert.assertEquals("HTTP @ proxy/<unresolved>:80", proxies.get(0).toString());
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
			final List<Proxy> proxies = pacScriptParser.discoverProxy("https://example.com");
			Assert.assertEquals(1, proxies.size());
			Assert.assertEquals("HTTP @ /10.0.0.1:8080", proxies.get(0).toString());
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testElseIf() {
		try {
			final String pacString = new String(PacScriptParserUtilities.toByteArray(getClass().getClassLoader().getResourceAsStream("test_ElseIf.pac")));
			final PacScriptParser pacScriptParser = new PacScriptParser(pacString);

			final List<Proxy> proxiesExample = pacScriptParser.discoverProxy("https://example.com");
			Assert.assertEquals(1, proxiesExample.size());
			Assert.assertEquals("HTTP @ proxy/<unresolved>:80", proxiesExample.get(0).toString());

			final List<Proxy> proxiesOther = pacScriptParser.discoverProxy("https://other.com");
			Assert.assertEquals(1, proxiesOther.size());
			Assert.assertNull(proxiesOther.get(0));

			final List<Proxy> proxiesSimple = pacScriptParser.discoverProxy("hostname");
			Assert.assertEquals(1, proxiesSimple.size());
			Assert.assertEquals("HTTP @ proxyElseIf/<unresolved>:80", proxiesSimple.get(0).toString());
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
}
