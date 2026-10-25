package de.soderer.pac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import de.soderer.pac.utilities.Method;

/**
 * Tests of the public API of {@link PacScriptParser}: loading PAC scripts from files and HTTP servers, parsing errors,
 * the result caches, the conversion of proxy settings into {@link Proxy} objects and the text representation of a script.
 */
@SuppressWarnings("static-method")
public class PacScriptParserApiTest {
	private static final String RETURN_DIRECT = "function FindProxyForURL(url, host) { return \"DIRECT\"; }";

	@TempDir
	Path tempDirectory;

	private static String returning(final String proxySettings) {
		return "function FindProxyForURL(url, host) { return \"" + proxySettings + "\"; }";
	}

	private static List<Proxy> discoverProxy(final String proxySettings) throws Exception {
		return new PacScriptParser(returning(proxySettings)).discoverProxy("http://example.com/", PacScriptParser.CacheType.None);
	}

	private static InetSocketAddress address(final Proxy proxy) {
		return (InetSocketAddress) proxy.address();
	}

	private static void assertParseError(final String expectedMessagePart, final String pacScript) {
		final Exception exception = assertThrows(Exception.class, () -> new PacScriptParser(pacScript).discoverProxySettings("http://example.com/"), pacScript);
		assertTrue(exception.getMessage().contains(expectedMessagePart), "Expected '" + expectedMessagePart + "' but was: " + exception.getMessage());
	}

	/**
	 * Starts a local HTTP server, which delivers the given data at "/proxy.pac" and returns 404 for other paths
	 */
	private static HttpServer startServer(final byte[] pacData) throws IOException {
		final HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> {
			if ("/proxy.pac".equals(exchange.getRequestURI().getPath())) {
				exchange.sendResponseHeaders(200, pacData.length);
				try (OutputStream outputStream = exchange.getResponseBody()) {
					outputStream.write(pacData);
				}
			} else {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
			}
		});
		server.start();
		return server;
	}

	private static String serverUrl(final HttpServer server, final String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	// ---- Loading ----

	@Test
	public void testLoadFromFileUrl() throws Exception {
		final Path pacFile = tempDirectory.resolve("proxy.pac");
		Files.write(pacFile, (returning("PROXY 10.0.0.1:3128") + "\r\n").getBytes(StandardCharsets.UTF_8));
		assertEquals(List.of("PROXY 10.0.0.1:3128"), new PacScriptParser(pacFile.toUri().toURL()).discoverProxySettings("http://example.com/"));
		assertEquals(List.of("PROXY 10.0.0.1:3128"), new PacScriptParser(pacFile.toUri().toURL(), 10).discoverProxySettings("http://example.com/"));
	}

	@Test
	public void testLoadFromMissingFile() {
		final Exception exception = assertThrows(RuntimeException.class, () -> new PacScriptParser(tempDirectory.resolve("missing.pac").toUri().toURL()));
		assertTrue(exception.getMessage().startsWith("Cannot read PAC data from URL"));
	}

	@Test
	public void testLoadFromHttpServer() throws Exception {
		final HttpServer server = startServer(returning("PROXY 10.0.0.2:8080").getBytes(StandardCharsets.UTF_8));
		try {
			// A text starting with "http" is used as URL of the PAC script
			assertEquals(List.of("PROXY 10.0.0.2:8080"), new PacScriptParser("  " + serverUrl(server, "/proxy.pac") + "  ").discoverProxySettings("http://example.com/"));
			assertEquals(List.of("PROXY 10.0.0.2:8080"), new PacScriptParser(serverUrl(server, "/proxy.pac"), 5).discoverProxySettings("http://example.com/"));

			final Exception exception = assertThrows(RuntimeException.class, () -> new PacScriptParser(serverUrl(server, "/missing.pac")));
			assertTrue(exception.getMessage().startsWith("Cannot read PAC data from URL"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void testOversizedPacScriptIsRejected() throws Exception {
		final byte[] oversizedData = new byte[5 * 1024 * 1024 + 1];
		Arrays.fill(oversizedData, (byte) ' ');
		final HttpServer server = startServer(oversizedData);
		try {
			final Exception exception = assertThrows(RuntimeException.class, () -> new PacScriptParser(serverUrl(server, "/proxy.pac")));
			assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
		} finally {
			server.stop(0);
		}
	}

	// ---- Parsing ----

	@Test
	public void testParsePacScript() throws Exception {
		final Map<String, Method> methods = new PacScriptParser("function FindProxyForURL(url, host) { return helper(host); }\nfunction helper(a) { return a; }\nfunction noParameters() { return 1; }").parsePacScript();
		assertEquals(3, methods.size());
		assertEquals(Arrays.asList("url", "host"), methods.get("FindProxyForURL").getMethodParameterNames());
		assertEquals(Arrays.asList("a"), methods.get("helper").getMethodParameterNames());
		assertTrue(methods.get("noParameters").getMethodParameterNames().isEmpty());
	}

	@Test
	public void testParseErrors() {
		assertParseError("PAC script is empty", "// only a comment");
		assertParseError("Unexpected code token: x", "function f x");
		assertParseError("Unexpected code token: b", "function f(a b) { }");
		assertParseError("Unexpected code token: x", "function f(a) x");
		assertParseError("Invalid PAC data found: trailing", RETURN_DIRECT + " trailing");
		assertParseError("Invalid PAC data found: var", "var x = 1;");
		assertParseError("Unbalanced brackets", "function FindProxyForURL(url, host) { return \"DIRECT\";");
		assertParseError("Unbalanced closing brackets", "function FindProxyForURL(url, host) { return (\"DIRECT\"]; }");
		assertParseError("PAC script does not define the required method 'FindProxyForURL'", "function other() { return \"DIRECT\"; }");
	}

	// ---- Proxy settings ----

	@Test
	public void testProxySettingsAreSplitAndTrimmed() throws Exception {
		assertEquals(List.of("PROXY 10.0.0.1:3128", "DIRECT"), new PacScriptParser(returning("  PROXY 10.0.0.1:3128 ;; DIRECT ; ")).discoverProxySettings("http://example.com/"));
		assertEquals(List.of(), new PacScriptParser(returning("")).discoverProxySettings("http://example.com/"));
	}

	@Test
	public void testProxyTypesAndDefaultPorts() throws Exception {
		final List<Proxy> proxies = discoverProxy("direct; PROXY 10.0.0.1; HTTP 10.0.0.2:81; HTTPS 10.0.0.3; SOCKS 10.0.0.4; SOCKS4 10.0.0.5:1081; SOCKS5 [::1]; proxy\t10.0.0.6:82");
		assertEquals(8, proxies.size());
		assertNull(proxies.get(0));
		assertEquals(Proxy.Type.HTTP, proxies.get(1).type());
		assertEquals(80, address(proxies.get(1)).getPort());
		assertEquals(81, address(proxies.get(2)).getPort());
		assertEquals(443, address(proxies.get(3)).getPort());
		assertEquals(Proxy.Type.SOCKS, proxies.get(4).type());
		assertEquals(1080, address(proxies.get(4)).getPort());
		assertEquals(1081, address(proxies.get(5)).getPort());
		assertEquals(1080, address(proxies.get(6)).getPort());
		assertTrue(address(proxies.get(6)).getAddress().isLoopbackAddress());
		assertEquals("10.0.0.6", address(proxies.get(7)).getHostString());
		assertEquals(82, address(proxies.get(7)).getPort());
	}

	@Test
	public void testInvalidProxySettings() {
		assertTrue(assertThrows(RuntimeException.class, () -> discoverProxy("PROXY")).getMessage().contains("Missing proxy address"));
		assertTrue(assertThrows(RuntimeException.class, () -> discoverProxy("FTP 10.0.0.1:21")).getMessage().contains("Unsupported proxy configuration type"));
		assertTrue(assertThrows(RuntimeException.class, () -> discoverProxy("PROXY 10.0.0.1:port")).getMessage().contains("Invalid port number"));
		assertTrue(assertThrows(RuntimeException.class, () -> discoverProxy("PROXY [::1")).getMessage().contains("Invalid proxy address"));
	}

	@Test
	public void testNoProxySettingsMeansDirect() throws Exception {
		final PacScriptParser parser = new PacScriptParser("function FindProxyForURL(url, host) { return 1; }");
		assertNull(parser.discoverProxySettings("http://example.com/"));
		final List<Proxy> proxies = parser.discoverProxy("http://example.com/");
		assertEquals(1, proxies.size());
		assertNull(proxies.get(0));
	}

	// ---- Caches ----

	@Test
	public void testCacheByDomain() throws Exception {
		final PacScriptParser parser = new PacScriptParser(RETURN_DIRECT);
		final List<String> result = parser.discoverProxySettings("http://www.example.com/a");
		// Default cache type: by domain, "www." is ignored
		assertSame(result, parser.discoverProxySettings("https://example.com/b"));
		assertSame(result, parser.discoverProxySettings("http://WWW.EXAMPLE.COM/c", PacScriptParser.CacheType.CacheByDomain));
		assertNotSame(result, parser.discoverProxySettings("http://other.example.com/a"));
	}

	@Test
	public void testCacheByFullUrl() throws Exception {
		final PacScriptParser parser = new PacScriptParser(RETURN_DIRECT);
		final List<String> result = parser.discoverProxySettings("http://example.com/a", PacScriptParser.CacheType.CacheByFullUrl);
		assertSame(result, parser.discoverProxySettings("http://example.com/a", PacScriptParser.CacheType.CacheByFullUrl));
		assertNotSame(result, parser.discoverProxySettings("http://example.com/b", PacScriptParser.CacheType.CacheByFullUrl));
		assertThrows(UnsupportedOperationException.class, () -> result.add("PROXY x:1"));
	}

	@Test
	public void testNoCache() throws Exception {
		final PacScriptParser parser = new PacScriptParser(RETURN_DIRECT);
		final List<String> result = parser.discoverProxySettings("http://example.com/a", PacScriptParser.CacheType.None);
		assertNotSame(result, parser.discoverProxySettings("http://example.com/a", PacScriptParser.CacheType.None));
		assertEquals(List.of("DIRECT"), result);
	}

	@Test
	public void testCacheSizeIsLimited() throws Exception {
		final PacScriptParser parser = new PacScriptParser(RETURN_DIRECT, 1);
		final List<String> resultA = parser.discoverProxySettings("http://a.example/");
		assertSame(resultA, parser.discoverProxySettings("http://a.example/"));
		parser.discoverProxySettings("http://b.example/");
		// The least recently used entry was removed
		assertNotSame(resultA, parser.discoverProxySettings("http://a.example/"));
	}

	@Test
	public void testCachedNullResult() throws Exception {
		final PacScriptParser parser = new PacScriptParser("function FindProxyForURL(url, host) { return; }");
		assertNull(parser.discoverProxySettings("http://example.com/", PacScriptParser.CacheType.CacheByFullUrl));
		assertNull(parser.discoverProxySettings("http://example.com/", PacScriptParser.CacheType.CacheByFullUrl));
		assertNull(parser.discoverProxySettings("http://example.com/"));
		assertNull(parser.discoverProxySettings("http://example.com/"));
	}

	@Test
	public void testDiscoverProxyWithCache() throws Exception {
		final PacScriptParser parser = new PacScriptParser(returning("PROXY 10.0.0.1:3128"));
		assertEquals(3128, address(parser.discoverProxy("http://example.com/", PacScriptParser.CacheType.CacheByFullUrl).get(0)).getPort());
		assertEquals(3128, address(parser.discoverProxy("http://example.com/").get(0)).getPort());
	}

	// ---- Domains ----

	@Test
	public void testGetDomainFromUrl() throws Exception {
		assertEquals("example.com", PacScriptParser.getDomainFromUrl("HTTP://WWW.Example.COM/path"));
		assertEquals("sub.example.com", PacScriptParser.getDomainFromUrl("https://sub.example.com:8443/path?x=1"));
		// URLs without protocol
		assertEquals("example.com", PacScriptParser.getDomainFromUrl("www.example.com/path"));
		assertEquals("example.com", PacScriptParser.getDomainFromUrl("example.com:8080/path"));
		// Invalid URI syntax
		assertEquals("exa mple.com", PacScriptParser.getDomainFromUrl("http://exa mple.com/"));
	}

	// ---- Text representation ----

	@Test
	public void testToString() throws Exception {
		final String pacScript = "function helper(a, b) { var sum = a + b; let text = \"x\"; const flag = !true; return sum; }\n"
				+ "function FindProxyForURL(url, host) {\n"
				+ "if (isPlainHostName(host)) { return \"DIRECT\"; } else { return \"PROXY 10.0.0.1:3128\"; }\n"
				+ "}\n"
				+ "function loops() { var n = 0; for (var i = 0; i < 3; i++) { n++; continue; } while (n < 5) { n += 1; break; } return; }";
		final String text = new PacScriptParser(pacScript).toString();

		// FindProxyForURL is the first method
		assertTrue(text.startsWith("function FindProxyForURL(url, host) {\n"), text);
		assertTrue(text.contains("\tif (isPlainHostName(host)) {\n\t\treturn \"DIRECT\";\n\t} else {\n\t\treturn \"PROXY 10.0.0.1:3128\";\n\t}"), text);
		assertTrue(text.contains("function helper(a, b) {"), text);
		assertTrue(text.contains("var sum = a + b;"), text);
		assertTrue(text.contains("let text = \"x\";"), text);
		assertTrue(text.contains("const flag = !true;"), text);
		assertTrue(text.contains("return sum;"), text);
		assertTrue(text.contains("for (var i = 0; i < 3; i = i + 1) {"), text);
		assertTrue(text.contains("continue;"), text);
		assertTrue(text.contains("while (n < 5) {"), text);
		assertTrue(text.contains("\t\tvar n = n + (1);\n\t\tbreak;"), text);
		assertTrue(text.contains("break;"), text);
		assertTrue(text.contains("return;"), text);
	}

	@Test
	public void testToStringWithoutFindProxyForUrl() throws Exception {
		assertEquals("function other() {\n\treturn 1;\n}\n", new PacScriptParser("function other() { return 1; }").toString());
	}
}
