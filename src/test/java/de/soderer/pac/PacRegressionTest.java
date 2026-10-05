package de.soderer.pac;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.soderer.pac.utilities.PacScriptMethods;
import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;

/**
 * Regression tests for bugs found during the Javadoc and bug review of the PAC library.
 */
@SuppressWarnings("static-method")
public class PacRegressionTest {
	@Test
	public void testProxyTypes() throws Exception {
		final PacScriptParser parser = new PacScriptParser("function FindProxyForURL(url, host) { return \"PROXY a:1; socks [::1]:3; HTTPS s; DIRECT;\"; }");
		final List<Proxy> proxies = parser.discoverProxy("http://example.com/x");
		Assertions.assertEquals(4, proxies.size());
		Assertions.assertEquals(Proxy.Type.HTTP, proxies.get(0).type());
		Assertions.assertEquals(Proxy.Type.SOCKS, proxies.get(1).type());
		Assertions.assertEquals(3, ((InetSocketAddress) proxies.get(1).address()).getPort());
		Assertions.assertEquals(443, ((InetSocketAddress) proxies.get(2).address()).getPort());
		Assertions.assertNull(proxies.get(3));
	}

	@Test
	public void testUrlWithoutProtocolAndHostParameter() throws Exception {
		// The host parameter of FindProxyForURL is the plain host name without port
		final PacScriptParser parser = new PacScriptParser("function FindProxyForURL(url, host) { return \"PROXY \" + host + \":8080\"; }");
		Assertions.assertEquals(List.of("PROXY example.com:8080"), parser.discoverProxySettings("http://user@example.com:8443/path?x", PacScriptParser.CacheType.None));
		Assertions.assertEquals(List.of("PROXY example.com:8080"), parser.discoverProxySettings("example.com/path"));
	}

	@Test
	public void testCachedResultIsUnmodifiable() throws Exception {
		final PacScriptParser parser = new PacScriptParser("function FindProxyForURL(url, host) { return \"DIRECT\"; }");
		Assertions.assertThrows(UnsupportedOperationException.class, () -> parser.discoverProxySettings("http://example.com").add("PROXY x:1"));
	}

	@Test
	public void testPacFunctions() {
		Assertions.assertFalse(PacScriptMethods.localHostOrDomainIs("w", "www.example.com"));
		Assertions.assertTrue(PacScriptMethods.localHostOrDomainIs("www", "www.example.com"));
		Assertions.assertTrue(PacScriptMethods.dnsDomainIs("WWW.Example.COM", ".example.com"));
		Assertions.assertTrue(PacScriptMethods.shExpMatch("ab", "a?"));
		Assertions.assertFalse(PacScriptMethods.shExpMatch("xzy", "x.y"));
		Assertions.assertTrue(PacScriptMethods.isInNetEx("10.0.0.1", "10.0.0.0/24"));
		Assertions.assertFalse(PacScriptMethods.isInNetEx("10.0.1.1", "10.0.0.0/24"));
		Assertions.assertTrue(PacScriptMethods.isInNetEx("2001:db8::1", "2001:db8::/32"));
		Assertions.assertFalse(PacScriptMethods.isInNetEx("10.0.0.1", "2001:db8::/32"));
		Assertions.assertEquals("127.0.0.1", PacScriptMethods.dnsResolveEx("127.0.0.1"));
	}

	@Test
	public void testOptionalParametersOfRangeFunctions() throws Exception {
		// Fewer parameters than the maximum must not fail
		final PacScriptParser parser = new PacScriptParser("function FindProxyForURL(url, host) { if (weekdayRange(\"SUN\", \"SAT\") && timeRange(0, 23)) { return \"DIRECT\"; } return \"PROXY x:1\"; }");
		Assertions.assertEquals(List.of("DIRECT"), parser.discoverProxySettings("http://example.com"));
	}

	@Test
	public void testProxyUrlParsing() throws Exception {
		final Proxy proxy = new ProxyConfiguration(ProxyConfigurationType.ProxyURL, "http://user:password@proxy.local:3128/").getProxy("http://example.com");
		Assertions.assertEquals("proxy.local", ((InetSocketAddress) proxy.address()).getHostString());
		Assertions.assertEquals(3128, ((InetSocketAddress) proxy.address()).getPort());
	}

	@Test
	public void testIsNumber() {
		Assertions.assertFalse(ProxyConfiguration.isNumber(""));
		Assertions.assertFalse(ProxyConfiguration.isNumber("|"));
		Assertions.assertTrue(ProxyConfiguration.isNumber("-1.5e3"));
	}

	@Test
	public void testEmptyScript() {
		Assertions.assertThrows(RuntimeException.class, () -> new PacScriptParser(" ").discoverProxySettings("http://example.com"));
	}
}
