package de.soderer.pac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.soderer.pac.utilities.ProxyConfiguration;
import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;

/**
 * Tests of {@link ProxyConfiguration}: all configuration types, the system properties, the environment variables (in a separate process),
 * PAC files with their cache and the proxy URL helpers.
 * Only IP addresses are used as proxy hosts, so no DNS lookup is needed.
 */
@SuppressWarnings("static-method")
public class ProxyConfigurationTest {
	private static final List<String> PROXY_PROPERTIES = Arrays.asList("http.proxyHost", "http.proxyPort", "http.nonProxyHosts", "https.proxyHost", "https.proxyPort");

	@TempDir
	Path tempDirectory;

	private final Map<String, String> savedProperties = new LinkedHashMap<>();

	@BeforeEach
	public void saveProxyProperties() {
		for (final String property : PROXY_PROPERTIES) {
			savedProperties.put(property, System.getProperty(property));
			System.clearProperty(property);
		}
	}

	@AfterEach
	public void restoreProxyProperties() {
		for (final Map.Entry<String, String> entry : savedProperties.entrySet()) {
			if (entry.getValue() == null) {
				System.clearProperty(entry.getKey());
			} else {
				System.setProperty(entry.getKey(), entry.getValue());
			}
		}
	}

	private static String proxyUrl(final Proxy proxy) {
		return ProxyConfiguration.getProxyURL(proxy);
	}

	private Path writePacFile(final String name, final String proxySettings) throws Exception {
		final Path pacFile = tempDirectory.resolve(name);
		Files.write(pacFile, ("function FindProxyForURL(url, host) { return \"" + proxySettings + "\"; }").getBytes(StandardCharsets.UTF_8));
		return pacFile;
	}

	// ---- Configuration types ----

	@Test
	public void testConfigurationTypeFromString() {
		assertSame(ProxyConfigurationType.None, ProxyConfigurationType.getFromString(null));
		assertSame(ProxyConfigurationType.None, ProxyConfigurationType.getFromString("unknown"));
		assertSame(ProxyConfigurationType.System, ProxyConfigurationType.getFromString("SYSTEM"));
		assertSame(ProxyConfigurationType.Environment, ProxyConfigurationType.getFromString("environment"));
		assertSame(ProxyConfigurationType.ProxyURL, ProxyConfigurationType.getFromString("proxy_url"));
		assertSame(ProxyConfigurationType.ProxyURL, ProxyConfigurationType.getFromString("Proxy-URL"));
		assertSame(ProxyConfigurationType.WPAD, ProxyConfigurationType.getFromString("wpad"));
		assertSame(ProxyConfigurationType.PACURL, ProxyConfigurationType.getFromString("pac-url"));
	}

	@Test
	public void testNoneConfiguration() throws Exception {
		final ProxyConfiguration configuration = new ProxyConfiguration(null);
		assertSame(ProxyConfigurationType.None, configuration.getProxyConfigurationType());
		assertNull(configuration.getProxyOrPacUrl());
		assertSame(Proxy.NO_PROXY, configuration.getProxy("http://example.com"));
		assertNull(configuration.getProxyURL("http://example.com"));
		assertSame(Proxy.NO_PROXY, new ProxyConfiguration(ProxyConfigurationType.None).getProxy("http://example.com"));
	}

	// ---- Proxy URL ----

	@Test
	public void testProxyUrlConfiguration() throws Exception {
		assertEquals("http://10.0.0.1:3128", new ProxyConfiguration(ProxyConfigurationType.ProxyURL, "http://user:secret@10.0.0.1:3128/path").getProxyURL("http://example.com"));
		assertEquals("http://10.0.0.1:8080", new ProxyConfiguration(ProxyConfigurationType.ProxyURL, " 10.0.0.1 ").getProxyURL("http://example.com"));
		final Proxy ipv6Proxy = new ProxyConfiguration(ProxyConfigurationType.ProxyURL, "[::1]:3129").getProxy("http://example.com");
		assertEquals(3129, ((InetSocketAddress) ipv6Proxy.address()).getPort());
		assertTrue(((InetSocketAddress) ipv6Proxy.address()).getAddress().isLoopbackAddress());
		assertEquals(8080, ((InetSocketAddress) new ProxyConfiguration(ProxyConfigurationType.ProxyURL, "[::1]").getProxy("http://example.com").address()).getPort());

		for (final String noProxy : new String[] { null, "", "  ", "direct", "DIRECT" }) {
			assertSame(Proxy.NO_PROXY, new ProxyConfiguration(ProxyConfigurationType.ProxyURL, noProxy).getProxy("http://example.com"), noProxy);
		}
		assertThrows(NumberFormatException.class, () -> new ProxyConfiguration(ProxyConfigurationType.ProxyURL, "10.0.0.1:port").getProxy("http://example.com"));
	}

	@Test
	public void testGetProxyUrlOfProxy() {
		assertNull(ProxyConfiguration.getProxyURL(Proxy.NO_PROXY));
		assertEquals("http://10.0.0.1:80", ProxyConfiguration.getProxyURL(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.0.0.1", 80))));
		assertEquals("socks://10.0.0.1:1080", ProxyConfiguration.getProxyURL(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("10.0.0.1", 1080))));
	}

	// ---- System properties ----

	@Test
	public void testSystemProxyWithoutProperties() throws Exception {
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("http://example.com"));
		System.setProperty("http.proxyHost", " ");
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("http://example.com"));
		System.setProperty("http.nonProxyHosts", "localhost");
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("http://example.com"));
	}

	@Test
	public void testSystemProxyHostAndPort() throws Exception {
		System.setProperty("http.proxyHost", "10.0.0.1");
		System.setProperty("http.proxyPort", " 3128 ");
		assertEquals("http://10.0.0.1:3128", proxyUrl(ProxyConfiguration.getSystemProxy("http://example.com")));
		assertEquals("http://10.0.0.1:3128", new ProxyConfiguration(ProxyConfigurationType.System).getProxyURL("http://example.com"));
		// Without https properties, the http properties are used for https
		assertEquals("http://10.0.0.1:3128", proxyUrl(ProxyConfiguration.getSystemProxy("https://example.com")));

		// Invalid or missing ports use the default port
		for (final String invalidPort : new String[] { "port", "0", "70000", "123456" }) {
			System.setProperty("http.proxyPort", invalidPort);
			assertEquals("http://10.0.0.1:8080", proxyUrl(ProxyConfiguration.getSystemProxy("http://example.com")), invalidPort);
		}
		System.clearProperty("http.proxyPort");
		assertEquals("http://10.0.0.1:8080", proxyUrl(ProxyConfiguration.getSystemProxy("http://example.com")));
	}

	@Test
	public void testSystemProxyForHttps() throws Exception {
		System.setProperty("http.proxyHost", "10.0.0.1");
		System.setProperty("http.proxyPort", "3128");
		System.setProperty("https.proxyHost", "10.0.0.2");
		System.setProperty("https.proxyPort", "3129");
		assertEquals("http://10.0.0.1:3128", proxyUrl(ProxyConfiguration.getSystemProxy("http://example.com")));
		assertEquals("http://10.0.0.2:3129", proxyUrl(ProxyConfiguration.getSystemProxy("HTTPS://example.com")));
	}

	@Test
	public void testSystemProxyNonProxyHosts() throws Exception {
		System.setProperty("http.proxyHost", "10.0.0.1");
		System.setProperty("http.proxyPort", "3128");
		System.setProperty("http.nonProxyHosts", "localhost| *.internal.example.com |intranet.example.org|10.*");

		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("http://localhost/x"));
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("http://host.internal.example.com/x"));
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("https://INTRANET.example.org/x"));
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("http://10.1.2.3/x"));
		// URLs without protocol
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("intranet.example.org/x"));
		// Invalid URLs are not sent to the proxy
		assertSame(Proxy.NO_PROXY, ProxyConfiguration.getSystemProxy("http://exa mple.com/"));

		assertEquals("http://10.0.0.1:3128", proxyUrl(ProxyConfiguration.getSystemProxy("http://example.com/x")));
		assertEquals("http://10.0.0.1:3128", proxyUrl(ProxyConfiguration.getSystemProxy("http://internal.example.com.attacker.net/x")));

		System.setProperty("http.proxyPort", "invalid");
		assertEquals("http://10.0.0.1:8080", proxyUrl(ProxyConfiguration.getSystemProxy("http://example.com/x")));
	}

	@Test
	public void testHostnamePatternMatches() {
		assertTrue(ProxyConfiguration.hostnamePatternMatches("www.example.com", "*.example.com"));
		assertTrue(ProxyConfiguration.hostnamePatternMatches("WWW.EXAMPLE.COM", "*.example.com"));
		assertTrue(ProxyConfiguration.hostnamePatternMatches("example.com", "example.*"));
		assertTrue(!ProxyConfiguration.hostnamePatternMatches("example.com", "*.example.com"));
		// Dots are no regex wildcards
		assertTrue(!ProxyConfiguration.hostnamePatternMatches("wwwXexample.com", "www.example.com"));
		// Regex special characters are escaped
		assertTrue(ProxyConfiguration.hostnamePatternMatches("a+b(c)", "a+b(c)"));
	}

	@Test
	public void testStringHelpers() {
		assertTrue(ProxyConfiguration.isBlank(null));
		assertTrue(ProxyConfiguration.isBlank(" \t"));
		assertTrue(ProxyConfiguration.isNotBlank("x"));
		assertTrue(ProxyConfiguration.isNumber("42"));
		assertTrue(ProxyConfiguration.isNumber("+.5"));
		assertTrue(ProxyConfiguration.isNumber("1E-3"));
		assertTrue(!ProxyConfiguration.isNumber(null));
		assertTrue(!ProxyConfiguration.isNumber("1.2.3"));
		assertTrue(!ProxyConfiguration.isNumber("e5"));
	}

	// ---- PAC files ----

	@Test
	public void testPacUrlConfiguration() throws Exception {
		final String pacUrl = writePacFile("proxy.pac", "PROXY 10.0.0.1:3128; DIRECT").toUri().toString();
		final ProxyConfiguration configuration = new ProxyConfiguration(ProxyConfigurationType.PACURL, pacUrl);
		assertEquals(pacUrl, configuration.getProxyOrPacUrl());
		// The first proxy setting is used
		assertEquals("http://10.0.0.1:3128", configuration.getProxyURL("http://example.com"));
	}

	@Test
	public void testPacUrlWithDirectOrEmptyResult() throws Exception {
		assertSame(Proxy.NO_PROXY, new ProxyConfiguration(ProxyConfigurationType.PACURL, writePacFile("direct.pac", "DIRECT; PROXY 10.0.0.1:3128").toUri().toString()).getProxy("http://example.com"));
		assertSame(Proxy.NO_PROXY, new ProxyConfiguration(ProxyConfigurationType.PACURL, writePacFile("empty.pac", "").toUri().toString()).getProxy("http://example.com"));

		final Path noStringResult = tempDirectory.resolve("number.pac");
		Files.write(noStringResult, "function FindProxyForURL(url, host) { return 1; }".getBytes(StandardCharsets.UTF_8));
		assertSame(Proxy.NO_PROXY, new ProxyConfiguration(ProxyConfigurationType.PACURL, noStringResult.toUri().toString()).getProxy("http://example.com"));

		for (final String noPacUrl : new String[] { null, " " }) {
			assertSame(Proxy.NO_PROXY, new ProxyConfiguration(ProxyConfigurationType.PACURL, noPacUrl).getProxy("http://example.com"));
		}
	}

	@Test
	public void testPacUrlErrors() throws Exception {
		final Path invalidPacFile = tempDirectory.resolve("invalid.pac");
		Files.write(invalidPacFile, "function FindProxyForURL(url, host) { return undefinedMethod(); }".getBytes(StandardCharsets.UTF_8));
		final Exception scriptError = assertThrows(Exception.class, () -> new ProxyConfiguration(ProxyConfigurationType.PACURL, invalidPacFile.toUri().toString()).getProxy("http://example.com"));
		assertTrue(scriptError.getMessage().startsWith("Cannot find proxy configuration for URL 'http://example.com' by using PAC file"));
		assertTrue(scriptError.getMessage().contains("Call of undefined method"));

		// Unknown protocol, relative URL and invalid URI syntax
		for (final String invalidPacUrl : new String[] { "unknownprotocol://host/proxy.pac", "relative/proxy.pac", "file:///exa mple.pac" }) {
			final Exception exception = assertThrows(Exception.class, () -> new ProxyConfiguration(ProxyConfigurationType.PACURL, invalidPacUrl).getProxy("http://example.com"), invalidPacUrl);
			assertTrue(exception.getMessage().startsWith("Cannot find proxy configuration"), exception.getMessage());
		}
		final Exception unknownProtocol = assertThrows(Exception.class, () -> new ProxyConfiguration(ProxyConfigurationType.PACURL, "unknownprotocol://host/proxy.pac").getProxy("http://example.com"));
		assertTrue(unknownProtocol.getMessage().contains("Invalid PAC url"));
	}

	@Test
	public void testPacScriptCache() throws Exception {
		final Path pacFile = writePacFile("cached.pac", "PROXY 10.0.0.1:3128");
		final ProxyConfiguration configuration = new ProxyConfiguration(ProxyConfigurationType.PACURL, pacFile.toUri().toString());
		assertEquals("http://10.0.0.1:3128", configuration.getProxyURL("http://a.example.com"));

		// The PAC script is cached without time limit by default
		writePacFile("cached.pac", "PROXY 10.0.0.2:3128");
		assertEquals("http://10.0.0.1:3128", configuration.getProxyURL("http://b.example.com"));
		assertSame(configuration, configuration.withPacScriptCacheMaxAge(60_000));
		assertEquals("http://10.0.0.1:3128", configuration.getProxyURL("http://c.example.com"));

		configuration.resetPacScriptCache();
		assertEquals("http://10.0.0.2:3128", configuration.getProxyURL("http://d.example.com"));

		// An expired PAC script is read again
		writePacFile("cached.pac", "PROXY 10.0.0.3:3128");
		configuration.setPacScriptCacheMaxAge(0);
		Thread.sleep(20);
		assertEquals("http://10.0.0.3:3128", configuration.getProxyURL("http://e.example.com"));
	}

	@Test
	public void testWpadWithGivenPacUrl() throws Exception {
		// With a given PAC URL no WPAD search is done
		final ProxyConfiguration configuration = new ProxyConfiguration(ProxyConfigurationType.WPAD, writePacFile("wpad.dat", "SOCKS 10.0.0.4:1080").toUri().toString());
		assertSame(configuration, configuration.withAllowInsecureHttpWpad(true));
		assertEquals("socks://10.0.0.4:1080", configuration.getProxyURL("http://example.com"));
	}

	// ---- Environment variables ----

	@Test
	public void testEnvironmentConfigurationUsesEnvironmentProxy() throws Exception {
		// The result depends on the environment of the test, so only the delegation is checked
		for (final String url : new String[] { "http://example.com", "https://example.com" }) {
			assertEquals(ProxyConfiguration.getEnvironmentProxy(url), new ProxyConfiguration(ProxyConfigurationType.Environment).getProxy(url));
		}
	}

	/**
	 * Runs {@link EnvironmentProxyProbe} in a separate process with the given proxy environment variables and returns the result for each URL
	 */
	private static Map<String, String> probeEnvironmentProxy(final Map<String, String> environment, final String... urls) throws Exception {
		final String javaExecutable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		final String classPath = new File(ProxyConfiguration.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath()
				+ File.pathSeparator + new File(EnvironmentProxyProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
		final List<String> command = new ArrayList<>(Arrays.asList(javaExecutable, "-cp", classPath));
		// A JaCoCo agent of the test process is used in the probe process too, so that its code coverage is recorded (the agent appends to the coverage file by default)
		for (final String jvmArgument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
			if (jvmArgument.startsWith("-javaagent:") && jvmArgument.toLowerCase(Locale.ROOT).contains("jacoco")) {
				command.add(jvmArgument);
			}
		}
		command.add(EnvironmentProxyProbe.class.getName());
		command.addAll(Arrays.asList(urls));

		final ProcessBuilder processBuilder = new ProcessBuilder(command);
		final Map<String, String> processEnvironment = processBuilder.environment();
		for (final String variable : new String[] { "HTTP_PROXY", "http_proxy", "HTTPS_PROXY", "https_proxy", "NO_PROXY", "no_proxy" }) {
			processEnvironment.remove(variable);
		}
		processEnvironment.putAll(environment);
		processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

		final Process process = processBuilder.start();
		final Map<String, String> results = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				final int separatorIndex = line.lastIndexOf('=');
				results.put(line.substring(0, separatorIndex), line.substring(separatorIndex + 1));
			}
		}
		assertTrue(process.waitFor(60, TimeUnit.SECONDS), "Probe process did not finish");
		assertEquals(0, process.exitValue(), "Probe process failed");
		return results;
	}

	private static boolean javaExecutableExists() {
		final File javaExecutable = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java"));
		return javaExecutable.exists();
	}

	@Test
	public void testEnvironmentProxy() throws Exception {
		assumeTrue(javaExecutableExists());

		final Map<String, String> noProxy = probeEnvironmentProxy(Map.of(), "http://example.com/");
		assertEquals("DIRECT", noProxy.get("http://example.com/"));

		final Map<String, String> httpOnly = probeEnvironmentProxy(Map.of("HTTP_PROXY", "http://10.0.0.1:3128"), "http://example.com/", "https://example.com/");
		assertEquals("http://10.0.0.1:3128", httpOnly.get("http://example.com/"));
		// Without HTTPS_PROXY the HTTP proxy is used for https
		assertEquals("http://10.0.0.1:3128", httpOnly.get("https://example.com/"));

		// Default ports depend on the protocol of the proxy URL
		final Map<String, String> defaultPorts = probeEnvironmentProxy(Map.of("HTTP_PROXY", "http://10.0.0.1", "HTTPS_PROXY", "https://10.0.0.2"), "http://example.com/", "https://example.com/");
		assertEquals("http://10.0.0.1:80", defaultPorts.get("http://example.com/"));
		assertEquals("http://10.0.0.2:443", defaultPorts.get("https://example.com/"));
		assertEquals("http://10.0.0.3:8080", probeEnvironmentProxy(Map.of("HTTP_PROXY", "10.0.0.3"), "http://example.com/").get("http://example.com/"));
		// Invalid ports are replaced by the default port
		assertEquals("http://10.0.0.4:8080", probeEnvironmentProxy(Map.of("HTTP_PROXY", "10.0.0.4:0"), "http://example.com/").get("http://example.com/"));
		assertEquals("http://10.0.0.4:8080", probeEnvironmentProxy(Map.of("HTTP_PROXY", "10.0.0.4:0", "NO_PROXY", "localhost"), "http://example.com/").get("http://example.com/"));
	}

	@Test
	public void testEnvironmentNoProxy() throws Exception {
		assumeTrue(javaExecutableExists());

		final Map<String, String> results = probeEnvironmentProxy(Map.of("HTTP_PROXY", "10.0.0.1:3128", "NO_PROXY", "localhost, .example.com,*.corp.net,,exact.org|other.org"),
				"http://localhost/", "http://www.example.com/", "http://example.com/", "http://a.b.corp.net/", "http://corp.net/", "http://exact.org/", "http://other.org/",
				"http://notexact.org/", "http://example.com.attacker.net/", "http://unrelated.com/", "http://exa mple.com/");
		for (final String directUrl : new String[] { "http://localhost/", "http://www.example.com/", "http://example.com/", "http://a.b.corp.net/", "http://corp.net/", "http://exact.org/", "http://other.org/" }) {
			assertEquals("DIRECT", results.get(directUrl), directUrl);
		}
		for (final String proxyUrl : new String[] { "http://notexact.org/", "http://example.com.attacker.net/", "http://unrelated.com/" }) {
			assertEquals("http://10.0.0.1:3128", results.get(proxyUrl), proxyUrl);
		}
		// Invalid URLs are not sent to the proxy
		assertEquals("DIRECT", results.get("http://exa mple.com/"));

		assertEquals("DIRECT", probeEnvironmentProxy(Map.of("HTTP_PROXY", "10.0.0.1:3128", "NO_PROXY", "*"), "http://example.com/").get("http://example.com/"));
	}

	@Test
	public void testEnvironmentLowerCaseVariables() throws Exception {
		assumeTrue(javaExecutableExists());
		// Environment variable names are case insensitive on Windows, so the lower case variants cannot be tested separately
		assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));

		final Map<String, String> results = probeEnvironmentProxy(Map.of("http_proxy", "10.0.0.1:3128", "https_proxy", "10.0.0.2:3129", "no_proxy", "intranet.local"),
				"http://example.com/", "https://example.com/", "http://intranet.local/");
		assertEquals("http://10.0.0.1:3128", results.get("http://example.com/"));
		assertEquals("http://10.0.0.2:3129", results.get("https://example.com/"));
		assertEquals("DIRECT", results.get("http://intranet.local/"));
	}
}
