package de.soderer.pac.utilities;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import de.soderer.pac.PacScriptParser;

/**
 * Determines the proxy to use for an URL by one of several configuration types: no proxy, the
 * JVM system properties, the environment variables, a fixed proxy URL, or a PAC script (given by
 * URL or discovered by WPAD).
 * <p>
 * A PAC script is downloaded and parsed once and then cached, optionally with a maximum age.
 * </p>
 */
public class ProxyConfiguration {
	/**
	 * Types of proxy configurations.
	 */
	public enum ProxyConfigurationType {
		/**
		 * No proxy, always a direct connection.
		 */
		None,
		/**
		 * Proxy of the JVM system properties "http.proxyHost", "https.proxyHost" and "http.nonProxyHosts".
		 */
		System,
		/**
		 * Proxy of the environment variables "HTTP_PROXY", "HTTPS_PROXY" and "NO_PROXY".
		 */
		Environment,
		/**
		 * A fixed proxy like "proxy.example.com:8080".
		 */
		ProxyURL,
		/**
		 * PAC script discovered by WPAD, see {@link PacScriptParser#findPacFileUrlByWpad(boolean)}.
		 */
		WPAD,
		/**
		 * PAC script downloaded from an URL.
		 */
		PACURL;

		/**
		 * Returns the type with the given name, ignoring case, "_" and "-".
		 *
		 * @param proxyConfigurationTypeString
		 *            the name, e.g. "pac-url"
		 * @return the type, {@link #None} for null and unknown names
		 */
		public static ProxyConfigurationType getFromString(final String proxyConfigurationTypeString) {
			if (proxyConfigurationTypeString == null) {
				return ProxyConfigurationType.None;
			} else {
				for (final ProxyConfigurationType dataType : ProxyConfigurationType.values()) {
					if (dataType.toString().equalsIgnoreCase(proxyConfigurationTypeString.replace("_", "").replace("-", ""))) {
						return dataType;
					}
				}
				return ProxyConfigurationType.None;
			}
		}
	}

	/**
	 * Type of the configuration.
	 */
	private final ProxyConfigurationType proxyConfigurationType;
	/**
	 * Proxy or PAC file URL, depending on the type.
	 */
	private String proxyOrPacUrl;
	/**
	 * Whether WPAD discovery is still to be done.
	 */
	private boolean searchByWpad = true;
	/**
	 * Whether WPAD may use plain HTTP.
	 */
	private boolean allowInsecureHttpWpad = false;

	/**
	 * Cached parser of the PAC script.
	 */
	private volatile PacScriptParser cachedPacScriptParser;
	/**
	 * Time the PAC script was loaded.
	 */
	private long pacScriptCacheTimestamp = 0;
	/**
	 * Maximum age of the cached PAC script, -1 for unlimited.
	 */
	private long pacScriptCacheMaxAgeMillis = -1; // -1 = unlimited

	/**
	 * Creates a configuration without URL, e.g. for System, Environment or WPAD.
	 *
	 * @param proxyConfigurationType
	 *            the type, null for {@link ProxyConfigurationType#None}
	 */
	public ProxyConfiguration(final ProxyConfigurationType proxyConfigurationType) {
		this(proxyConfigurationType, null);
	}

	/**
	 * Creates a configuration.
	 *
	 * @param proxyConfigurationType
	 *            the type, null for {@link ProxyConfigurationType#None}
	 * @param proxyOrPacUrl
	 *            the proxy for ProxyURL like "http://proxy.example.com:8080", the PAC file URL for PACURL,
	 *            or an optional PAC file URL for WPAD to skip the discovery
	 */
	public ProxyConfiguration(final ProxyConfigurationType proxyConfigurationType, final String proxyOrPacUrl) {
		if (proxyConfigurationType == null) {
			this.proxyConfigurationType = ProxyConfigurationType.None;
		} else {
			this.proxyConfigurationType = proxyConfigurationType;
		}

		this.proxyOrPacUrl = proxyOrPacUrl;
	}

	/**
	 * Returns the type of the configuration.
	 *
	 * @return the type
	 */
	public ProxyConfigurationType getProxyConfigurationType() {
		return proxyConfigurationType;
	}

	/**
	 * Returns the proxy or PAC file URL. For WPAD, this is the discovered PAC file URL after the
	 * first {@link #getProxy(String)} call.
	 *
	 * @return the URL, or null
	 */
	public String getProxyOrPacUrl() {
		return proxyOrPacUrl;
	}

	/**
	 * Enables the plain-HTTP WPAD fallback (http://wpad.&lt;domain&gt;/wpad.dat) in
	 * addition to the HTTPS candidate. Disabled by default because it allows
	 * unauthenticated, unencrypted PAC file discovery, which is a well-known
	 * man-in-the-middle vector. Only enable this if you understand and accept
	 * that risk (e.g. in a trusted, controlled corporate network).
	 *
	 * @param allowInsecureHttpWpad
	 *            true to also try plain HTTP
	 */
	public void setAllowInsecureHttpWpad(final boolean allowInsecureHttpWpad) {
		this.allowInsecureHttpWpad = allowInsecureHttpWpad;
	}

	/**
	 * Enables the plain-HTTP WPAD fallback, see {@link #setAllowInsecureHttpWpad(boolean)}.
	 *
	 * @param newAllowInsecureHttpWpad
	 *            true to also try plain HTTP
	 * @return this configuration for chaining
	 */
	public ProxyConfiguration withAllowInsecureHttpWpad(final boolean newAllowInsecureHttpWpad) {
		setAllowInsecureHttpWpad(newAllowInsecureHttpWpad);
		return this;
	}

	/**
	 * Sets an optional maximum age for the cached, parsed PAC script (only
	 * relevant for ProxyConfigurationType.PACURL and WPAD). After this many
	 * milliseconds, the PAC script is re-downloaded and re-parsed on the next
	 * getProxy() call, in case the remote PAC file has changed in the meantime.
	 * Default is -1 (cache forever once loaded, until resetPacScriptCache() is
	 * called manually).
	 *
	 * @param maxAgeMillis
	 *            the maximum age in milliseconds, -1 for unlimited
	 */
	public void setPacScriptCacheMaxAge(final long maxAgeMillis) {
		pacScriptCacheMaxAgeMillis = maxAgeMillis;
	}

	/**
	 * Sets an optional maximum age for the cached PAC script, see {@link #setPacScriptCacheMaxAge(long)}.
	 *
	 * @param newMaxAgeMillis
	 *            the maximum age in milliseconds, -1 for unlimited
	 * @return this configuration for chaining
	 */
	public ProxyConfiguration withPacScriptCacheMaxAge(final long newMaxAgeMillis) {
		setPacScriptCacheMaxAge(newMaxAgeMillis);
		return this;
	}

	/**
	 * Forces the next getProxy() call to re-download and re-parse the PAC
	 * script, discarding the cached parser instance and its internal
	 * proxy-by-domain result cache.
	 */
	public synchronized void resetPacScriptCache() {
		cachedPacScriptParser = null;
	}

	private PacScriptParser getOrCreatePacScriptParser() throws Exception {
		PacScriptParser parser = cachedPacScriptParser;
		if (parser == null || isPacScriptCacheExpired()) {
			synchronized (this) {
				parser = cachedPacScriptParser;
				if (parser == null || isPacScriptCacheExpired()) {
					final URL pacUrl;
					try {
						pacUrl = new URI(proxyOrPacUrl).toURL();
					} catch (final MalformedURLException e) {
						throw new RuntimeException("Invalid PAC url: " + proxyOrPacUrl, e);
					}
					parser = new PacScriptParser(pacUrl);
					cachedPacScriptParser = parser;
					pacScriptCacheTimestamp = System.currentTimeMillis();
				}
			}
		}
		return parser;
	}

	private boolean isPacScriptCacheExpired() {
		return pacScriptCacheMaxAgeMillis >= 0
				&& (System.currentTimeMillis() - pacScriptCacheTimestamp) > pacScriptCacheMaxAgeMillis;
	}

	/**
	 * Returns the proxy to use for an URL. For a PAC script, the first proxy of its result is used.
	 *
	 * @param url
	 *            the URL to connect to
	 * @return the proxy, {@link Proxy#NO_PROXY} for a direct connection
	 * @throws Exception
	 *             if the proxy URL is invalid, or the PAC script cannot be loaded or executed
	 */
	public Proxy getProxy(final String url) throws Exception {
		switch (proxyConfigurationType) {
			case None:
				return Proxy.NO_PROXY;
			case System:
				return getSystemProxy(url);
			case Environment:
				return getEnvironmentProxy(url);
			case ProxyURL:
				if (proxyOrPacUrl == null || proxyOrPacUrl.trim().length() == 0 || "DIRECT".equalsIgnoreCase(proxyOrPacUrl)) {
					return Proxy.NO_PROXY;
				} else {
					return new Proxy(Proxy.Type.HTTP, parseProxyAddress(proxyOrPacUrl, 8080));
				}
			case WPAD:
				synchronized (this) {
					// Discover the PAC file only once, also if multiple threads ask at the same time
					if ((proxyOrPacUrl == null || proxyOrPacUrl.trim().length() == 0) && searchByWpad) {
						proxyOrPacUrl = PacScriptParser.findPacFileUrlByWpad(allowInsecureHttpWpad);
						searchByWpad = false;
					}
				}
				//$FALL-THROUGH$
			case PACURL:
				if (proxyOrPacUrl == null || proxyOrPacUrl.trim().length() == 0) {
					return Proxy.NO_PROXY;
				} else {
					try {
						final List<Proxy> multipleAllowedProxySettingsForThisUrl = getOrCreatePacScriptParser().discoverProxy(url);
						if (multipleAllowedProxySettingsForThisUrl == null || multipleAllowedProxySettingsForThisUrl.isEmpty()) {
							return Proxy.NO_PROXY;
						} else {
							final Proxy proxy = multipleAllowedProxySettingsForThisUrl.get(0);
							if (proxy == null) {
								return Proxy.NO_PROXY;
							} else {
								return proxy;
							}
						}
					} catch (final Exception e) {
						throw new Exception("Cannot find proxy configuration for URL '" + url + "' by using PAC file '" + proxyOrPacUrl + "': " + e.getMessage(), e);
					}
				}
			default:
				return Proxy.NO_PROXY;
		}
	}

	/**
	 * Returns the proxy to use for an URL as text, see {@link #getProxy(String)}.
	 *
	 * @param url
	 *            the URL to connect to
	 * @return the proxy like "http://proxy.example.com:8080", or null for a direct connection
	 * @throws Exception
	 *             if the proxy URL is invalid, or the PAC script cannot be loaded or executed
	 */
	public String getProxyURL(final String url) throws Exception {
		final Proxy proxy = getProxy(url);
		return getProxyURL(proxy);
	}

	/**
	 * Returns a proxy as text.
	 *
	 * @param proxy
	 *            the proxy
	 * @return the proxy like "http://proxy.example.com:8080" or "socks://host:1080", or null for a
	 *         direct connection
	 */
	public static String getProxyURL(final Proxy proxy) {
		final InetSocketAddress address = (InetSocketAddress) proxy.address();
		if (address == null) {
			return null;
		} else {
			final String host = address.getHostString();
			final int port = address.getPort();
			String protocol;
			switch (proxy.type()) {
				case HTTP:
					protocol = "http";
					break;
				case SOCKS:
					protocol = "socks";
					break;
				case DIRECT:
					return null;
				default:
					protocol = "http";
			}
			return protocol + "://" + host + ":" + port;
		}
	}

	/**
	 * System proxy configuration is set via JVM properties on startup or via environment properties:<br />
	 * Watch out: http.nonProxyHosts is used for both protocol types<br />
	 * java ... -Dhttp.proxyHost=proxy.url.local -Dhttp.proxyPort=8080 -Dhttp.nonProxyHosts='127.0.0.1|localhost'
	 * java ... -Dhttps.proxyHost=proxy.url.local -Dhttps.proxyPort=8080 -Dhttp.nonProxyHosts='127.0.0.1|localhost'
	 *
	 * @param url
	 *            the URL to connect to
	 * @return the proxy, {@link Proxy#NO_PROXY} for a direct connection
	 * @throws Exception
	 *             never, declared for compatibility
	 */
	public static Proxy getSystemProxy(final String url) throws Exception {
		String proxyHost = System.getProperty("http.proxyHost");
		String proxyPort = System.getProperty("http.proxyPort");
		final String nonProxyHosts = System.getProperty("http.nonProxyHosts");

		if (url.toLowerCase(Locale.ROOT).startsWith("https:")) {
			if (System.getProperty("https.proxyHost") != null) {
				proxyHost = System.getProperty("https.proxyHost");
			}
			if (System.getProperty("https.proxyPort") != null) {
				proxyPort = System.getProperty("https.proxyPort");
			}
			// The https protocol uses the same nonProxyHosts as http
			// So there is only http.nonProxyHosts and no https.nonProxyHosts
		}

		if (isBlank(proxyHost)) {
			return Proxy.NO_PROXY;
		} else {

			if (isBlank(nonProxyHosts)) {
				if (isNotBlank(proxyHost)) {
					if (isPort(proxyPort)) {
						return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort.trim())));
					} else {
						return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, 8080));
					}
				} else {
					return Proxy.NO_PROXY;
				}
			} else {
				boolean ignoreProxy = false;
				final String urlDomain = getDomainFromUrl(url);
				for (String nonProxyHostWithWildcard : nonProxyHosts.split("\\|")) {
					nonProxyHostWithWildcard = nonProxyHostWithWildcard.trim();
					if (urlDomain == null || urlDomain.equalsIgnoreCase(nonProxyHostWithWildcard)) {
						ignoreProxy = true;
						break;
					} else if (hostnamePatternMatches(urlDomain, nonProxyHostWithWildcard)) {
						ignoreProxy = true;
						break;
					}
				}
				if (!ignoreProxy) {
					if (isNotBlank(proxyHost)) {
						if (isPort(proxyPort)) {
							return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort.trim())));
						} else {
							return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, 8080));
						}
					} else {
						return Proxy.NO_PROXY;
					}
				} else {
					return Proxy.NO_PROXY;
				}
			}
		}
	}

	/**
	 * Environment proxy configuration is set operating system environment properties:<br />
	 *  set HTTP_PROXY=proxy.url.local:8080
	 *  set HTTP_PROXY=other.proxy.url.local:8080
	 *  set NO_PROXY=127.0.0.1,localhost<br />
	 * A NO_PROXY entry matches the host itself and its subdomains, "*" matches all hosts.
	 *
	 * @param url
	 *            the URL to connect to
	 * @return the proxy, {@link Proxy#NO_PROXY} for a direct connection
	 * @throws Exception
	 *             if the proxy port is invalid
	 */
	public static Proxy getEnvironmentProxy(final String url) throws Exception {
		String proxyHost = System.getenv("HTTP_PROXY");
		if (proxyHost == null) {
			proxyHost = System.getenv("http_proxy");
		}

		if (url.toLowerCase(Locale.ROOT).startsWith("https:")) {
			String proxyHostHttps = System.getenv("HTTPS_PROXY");
			if (proxyHostHttps == null) {
				proxyHostHttps = System.getenv("https_proxy");
			}

			if (proxyHostHttps != null) {
				proxyHost = proxyHostHttps;
			}
		}

		if (isBlank(proxyHost)) {
			return Proxy.NO_PROXY;
		} else {
			final String defaultPort = proxyHost.trim().toLowerCase(Locale.ROOT).startsWith("https://") ? "443" : proxyHost.trim().toLowerCase(Locale.ROOT).startsWith("http://") ? "80" : "8080";
			final InetSocketAddress proxyAddress = parseProxyAddress(proxyHost, Integer.parseInt(defaultPort));
			proxyHost = proxyAddress.getHostString();
			final String proxyPort = Integer.toString(proxyAddress.getPort());

			String nonProxyHosts = System.getenv("NO_PROXY");
			if (nonProxyHosts == null) {
				nonProxyHosts = System.getenv("no_proxy");
			}

			if (isBlank(nonProxyHosts)) {
				if (isNotBlank(proxyHost)) {
					if (isPort(proxyPort)) {
						return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort.trim())));
					} else {
						return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, 8080));
					}
				} else {
					return Proxy.NO_PROXY;
				}
			} else {
				boolean ignoreProxy = false;
				final String urlDomain = getDomainFromUrl(url);
				for (String nonProxyHost : nonProxyHosts.split("\\||,")) {
					nonProxyHost = nonProxyHost.trim();
					if (nonProxyHost.isEmpty()) {
						// Empty entry, e.g. after a trailing ',': matches no host
						continue;
					} else if (urlDomain == null || "*".equals(nonProxyHost) || matchesNoProxyEntry(urlDomain, nonProxyHost)) {
						ignoreProxy = true;
						break;
					}
				}
				if (!ignoreProxy) {
					if (isNotBlank(proxyHost)) {
						if (isPort(proxyPort)) {
							return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort.trim())));
						} else {
							return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, 8080));
						}
					} else {
						return Proxy.NO_PROXY;
					}
				} else {
					return Proxy.NO_PROXY;
				}
			}
		}
	}

	private static String getDomainFromUrl(String url) {
		if (!url.toLowerCase(Locale.ROOT).startsWith("http://") && !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
			url = "http://" + url;
		}
		try {
			return new URI(url).toURL().getHost();
		} catch (@SuppressWarnings("unused") final Exception e) {
			return null;
		}
	}

	/**
	 * Checks a host against a NO_PROXY entry: the host itself or a subdomain of it, so "example.com"
	 * matches "example.com" and "www.example.com", but not "notexample.com".
	 *
	 * @param host
	 *            the host
	 * @param noProxyEntry
	 *            the NO_PROXY entry, optionally with leading "." or "*."
	 * @return true, if the host matches
	 */
	private static boolean matchesNoProxyEntry(final String host, final String noProxyEntry) {
		String domain = noProxyEntry.toLowerCase(Locale.ROOT);
		if (domain.startsWith("*.")) {
			domain = domain.substring(2);
		} else if (domain.startsWith(".")) {
			domain = domain.substring(1);
		}
		final String lowerCaseHost = host.toLowerCase(Locale.ROOT);
		return lowerCaseHost.equals(domain) || lowerCaseHost.endsWith("." + domain);
	}

	/**
	 * Parses a proxy address like "proxy:8080", "http://user:password@proxy:8080/" or "[::1]:8080".
	 * Protocol, user information and path are ignored.
	 *
	 * @param proxyString
	 *            the proxy address
	 * @param defaultPort
	 *            the port if none is given
	 * @return the socket address
	 * @throws NumberFormatException
	 *             if the port is invalid
	 */
	private static InetSocketAddress parseProxyAddress(final String proxyString, final int defaultPort) {
		String proxyHost = proxyString.trim();
		if (proxyHost.contains("://")) {
			proxyHost = proxyHost.substring(proxyHost.indexOf("://") + 3);
		}
		if (proxyHost.indexOf('/') >= 0) {
			proxyHost = proxyHost.substring(0, proxyHost.indexOf('/'));
		}
		if (proxyHost.indexOf('@') >= 0) {
			proxyHost = proxyHost.substring(proxyHost.lastIndexOf('@') + 1);
		}
		int proxyPort = defaultPort;
		if (proxyHost.startsWith("[")) {
			final int closingBracketIndex = proxyHost.indexOf(']');
			if (closingBracketIndex > 0) {
				if (proxyHost.length() > closingBracketIndex + 1 && proxyHost.charAt(closingBracketIndex + 1) == ':') {
					proxyPort = Integer.parseInt(proxyHost.substring(closingBracketIndex + 2));
				}
				proxyHost = proxyHost.substring(1, closingBracketIndex);
			}
		} else if (proxyHost.contains(":")) {
			proxyPort = Integer.parseInt(proxyHost.substring(proxyHost.lastIndexOf(':') + 1));
			proxyHost = proxyHost.substring(0, proxyHost.lastIndexOf(':'));
		}
		return new InetSocketAddress(proxyHost, proxyPort);
	}

	/**
	 * Checks for a valid port number.
	 *
	 * @param portString
	 *            the text
	 * @return true, if the text is a number from 1 to 65535
	 */
	private static boolean isPort(final String portString) {
		if (portString == null || !portString.trim().matches("[0-9]{1,5}")) {
			return false;
		}
		final int port = Integer.parseInt(portString.trim());
		return port >= 1 && port <= 65535;
	}

	/**
	 * Checks whether a text is null, empty or contains only whitespace.
	 *
	 * @param value
	 *            the text
	 * @return true, if the text is blank
	 */
	public static boolean isBlank(final String value) {
		return value == null || value.length() == 0 || value.trim().length() == 0;
	}

	/**
	 * Checks whether a text contains other characters than whitespace.
	 *
	 * @param value
	 *            the text
	 * @return true, if the text is not blank
	 */
	public static boolean isNotBlank(final String value) {
		return !isBlank(value);
	}

	/**
	 * Checks for a decimal number with optional sign, fraction and exponent, like "-1.5e3".
	 *
	 * @param numberString
	 *            the text
	 * @return true, if the text is a number
	 */
	public static boolean isNumber(final String numberString) {
		return numberString != null && Pattern.matches("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?", numberString);
	}

	/**
	 * Checks a host name against a pattern with "*" wildcards, ignoring case.
	 *
	 * @param hostname
	 *            the host name
	 * @param hostnamePattern
	 *            the pattern like "*.example.com"
	 * @return true, if the host name matches
	 */
	public static boolean hostnamePatternMatches(final String hostname, final String hostnamePattern) {
		final StringBuilder hostnamePatternEscaped = new StringBuilder();
		for (final char c : hostnamePattern.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				hostnamePatternEscaped.append(c);
			} else if ('*' == c) {
				hostnamePatternEscaped.append(".*");
			} else {
				hostnamePatternEscaped.append("\\");
				hostnamePatternEscaped.append(c);
			}
		}

		return Pattern.matches(hostnamePatternEscaped.toString().toLowerCase(Locale.ROOT), hostname.toLowerCase(Locale.ROOT));
	}
}
