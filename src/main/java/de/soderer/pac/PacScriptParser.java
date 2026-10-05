package de.soderer.pac;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.soderer.pac.utilities.Context;
import de.soderer.pac.utilities.Method;
import de.soderer.pac.utilities.PacScriptParserUtilities;

/**
 * Parser and interpreter for proxy auto-config (PAC) scripts.
 * <p>
 * A PAC script is a JavaScript file with the function FindProxyForURL(url, host), which returns
 * the proxies to use for an URL, e.g. "PROXY proxy.example.com:8080; DIRECT". This class
 * interprets the JavaScript subset used in PAC files without a JavaScript engine, including the
 * standard PAC functions like dnsDomainIs, isInNet or shExpMatch (see {@link de.soderer.pac.utilities.PacScriptMethods}).
 * The script must consist of function definitions only.
 * </p>
 * <p>
 * Results are cached in a size-bounded LRU cache, by default per domain. Instances are thread-safe.
 * </p>
 */
public class PacScriptParser {
	/**
	 * Default maximum number of cached results.
	 */
	private static final int DEFAULT_MAX_PAC_PROXY_CACHE_ENTRIES = 1000;

	/**
	 * The PAC script text.
	 */
	private String pacScriptData = null;
	/**
	 * The parsed functions of the script, parsed on first use.
	 */
	private volatile Map<String, Method> pacScriptMethods = null;

	/**
	 * Cached results by domain or URL.
	 */
	private final Map<String, List<String>> pacProxyCache;

	/**
	 * Caching of the results of FindProxyForURL. Default: CacheByDomain
	 */
	public enum CacheType {
		/**
		 * No caching, the script is executed for every call.
		 */
		None,
		/**
		 * One result per domain ("www." is ignored). Fast, but wrong for scripts that check the URL path.
		 */
		CacheByDomain,
		/**
		 * One result per full URL.
		 */
		CacheByFullUrl
	}

	/**
	 * Creates a parser for a PAC script downloaded from an URL.
	 *
	 * @param pacUrl
	 *            the URL of the PAC file
	 * @throws RuntimeException
	 *             if the PAC file cannot be read or exceeds 5 MB
	 */
	public PacScriptParser(final URL pacUrl) {
		this(pacUrl, DEFAULT_MAX_PAC_PROXY_CACHE_ENTRIES);
	}

	/**
	 * Creates a parser for a PAC script downloaded from an URL.
	 *
	 * @param pacUrl
	 *            the URL of the PAC file
	 * @param maxPacProxyCacheEntries
	 *            the maximum number of cached results
	 * @throws RuntimeException
	 *             if the PAC file cannot be read or exceeds 5 MB
	 */
	public PacScriptParser(final URL pacUrl, final int maxPacProxyCacheEntries) {
		pacScriptData = PacScriptParserUtilities.readPacData(pacUrl);
		pacProxyCache = createBoundedCache(maxPacProxyCacheEntries);
	}

	/**
	 * Creates a parser for a PAC script text, or for an URL starting with "http".
	 *
	 * @param pacScriptData
	 *            the PAC script, or the URL of the PAC file
	 * @throws Exception
	 *             if the PAC file cannot be read
	 */
	public PacScriptParser(final String pacScriptData) throws Exception {
		this(pacScriptData, DEFAULT_MAX_PAC_PROXY_CACHE_ENTRIES);
	}

	/**
	 * Creates a parser for a PAC script text, or for an URL starting with "http".
	 *
	 * @param pacScriptData
	 *            the PAC script, or the URL of the PAC file
	 * @param maxPacProxyCacheEntries
	 *            the maximum number of cached results
	 * @throws Exception
	 *             if the PAC file cannot be read
	 */
	public PacScriptParser(final String pacScriptData, final int maxPacProxyCacheEntries) throws Exception {
		if (pacScriptData.trim().toLowerCase(Locale.ROOT).startsWith("http")) {
			this.pacScriptData = PacScriptParserUtilities.readPacData(URI.create(pacScriptData.trim()).toURL());
		} else {
			this.pacScriptData = pacScriptData;
		}
		pacProxyCache = createBoundedCache(maxPacProxyCacheEntries);
	}

	/**
	 * Creates a size-bounded, thread-safe LRU cache: once maxEntries is
	 * exceeded, the least-recently-used entry (by access, not just insertion)
	 * is evicted automatically on the next put().
	 *
	 * Synchronized because a single PacScriptParser instance (and therefore
	 * this cache) may be shared and accessed concurrently across multiple
	 * threads, e.g. when cached by ProxyConfiguration for repeated getProxy()
	 * calls.
	 */
	private static Map<String, List<String>> createBoundedCache(final int maxEntries) {
		final Map<String, List<String>> lruMap = new LinkedHashMap<>(16, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(final Map.Entry<String, List<String>> eldest) {
				return size() > maxEntries;
			}
		};
		return Collections.synchronizedMap(lruMap);
	}

	/**
	 * Parses the function definitions of the PAC script.
	 *
	 * @return the functions by name
	 * @throws RuntimeException
	 *             if the script is empty or invalid
	 */
	public Map<String, Method> parsePacScript() {
		final Map<String, Method> methodDefinitions = new HashMap<>();

		List<String> pacScriptTokens = PacScriptParserUtilities.tokenize(PacScriptParserUtilities.removeComments(pacScriptData));

		pacScriptTokens = PacScriptParserUtilities.replaceAliases(pacScriptTokens);

		if (pacScriptTokens.isEmpty()) {
			throw new RuntimeException("PAC script is empty");
		}

		int tokenIndex = 0;
		String nextToken = pacScriptTokens.get(tokenIndex);
		while ("function".equals(nextToken)) {
			tokenIndex++;
			final String methodName = pacScriptTokens.get(tokenIndex);
			tokenIndex++;
			nextToken = pacScriptTokens.get(tokenIndex);
			if (!"(".equals(nextToken)) {
				throw new RuntimeException("Unexpected code token: " + nextToken);
			}
			tokenIndex++;
			nextToken = pacScriptTokens.get(tokenIndex);
			final List<String> methodParameterNames = new ArrayList<>();
			while (!")".equals(nextToken)) {
				if (methodParameterNames.size() > 0) {
					if (!",".equals(nextToken)) {
						throw new RuntimeException("Unexpected code token: " + nextToken);
					} else {
						tokenIndex++;
						nextToken = pacScriptTokens.get(tokenIndex);
					}
				}
				methodParameterNames.add(nextToken);
				tokenIndex++;
				nextToken = pacScriptTokens.get(tokenIndex);
			}
			tokenIndex++;
			nextToken = pacScriptTokens.get(tokenIndex);
			if (!"{".equals(nextToken)) {
				throw new RuntimeException("Unexpected code token: " + nextToken);
			}
			final int methodBlockStart = tokenIndex;
			final int methodBlockEnd = PacScriptParserUtilities.findClosingBracketToken(pacScriptTokens, tokenIndex);

			methodDefinitions.put(methodName, new Method(methodName, methodParameterNames, pacScriptTokens.subList(methodBlockStart + 1, methodBlockEnd)));
			tokenIndex = methodBlockEnd;

			tokenIndex++;
			if (pacScriptTokens.size() > tokenIndex) {
				nextToken = pacScriptTokens.get(tokenIndex);
			}
		}

		if (pacScriptTokens.size() != tokenIndex) {
			throw new RuntimeException("Invalid PAC data found: " + nextToken);
		}

		return methodDefinitions;
	}

	/**
	 * Attempts to discover a PAC file URL via WPAD using HTTPS only, see
	 * {@link #findPacFileUrlByWpad(boolean)}.
	 *
	 * @return the URL of the PAC file, or null if none was found
	 */
	public static String findPacFileUrlByWpad() {
		return findPacFileUrlByWpad(false);
	}

	/**
	 * Attempts to discover a PAC file URL via WPAD (Web Proxy Auto-Discovery).
	 *
	 * SECURITY WARNING: WPAD is a well-known attack vector. Any device on the
	 * local network (or a compromised DHCP/DNS server) can potentially answer
	 * WPAD requests and serve a malicious PAC script, allowing an attacker to
	 * redirect some or all of the application's traffic through a proxy under
	 * their control (a classic man-in-the-middle setup). This method performs
	 * no authenticity or integrity verification of the discovered PAC file.
	 *
	 * By default, only the HTTPS-based candidate is attempted. Pass
	 * allowInsecureHttpWpad = true only if you understand and accept the risk
	 * of unauthenticated, unencrypted PAC file discovery over plain HTTP.
	 *
	 * @param allowInsecureHttpWpad whether to also try plain-HTTP wpad.dat candidates
	 * @return the URL of the PAC file, or null if none was found
	 */
	public static String findPacFileUrlByWpad(final boolean allowInsecureHttpWpad) {
		try {
			final String fqdnAddress = InetAddress.getLocalHost().getCanonicalHostName();
			String fullDomain;
			if (fqdnAddress.contains(".")) {
				fullDomain = fqdnAddress.substring(fqdnAddress.indexOf(".") + 1);
			} else {
				return null;
			}

			final String[] domainParts = fullDomain.split("\\.");
			final List<String> pacUrlCandidates = new ArrayList<>();
			pacUrlCandidates.add("https://proxypac." + fullDomain + "/proxy.pac");

			if (allowInsecureHttpWpad) {
				// Exclude TLD domain like 'com' from pacUrlCandidates
				for (int i = 0; i < domainParts.length - 1; i++) {
					final String subDomain = PacScriptParserUtilities.join(Arrays.copyOfRange(domainParts, i, domainParts.length), ".");
					pacUrlCandidates.add("http://wpad." + subDomain + "/wpad.dat");
				}
			}

			for (final String pacUrlCandidate : pacUrlCandidates) {
				try {
					final URLConnection pacConnection = URI.create(pacUrlCandidate).toURL().openConnection();
					pacConnection.setConnectTimeout(3_000);
					pacConnection.setReadTimeout(3_000);
					pacConnection.connect();
				} catch (@SuppressWarnings("unused") final Exception e) {
					continue;
				}
				return pacUrlCandidate;
			}
			return null;
		} catch (@SuppressWarnings("unused") final UnknownHostException e) {
			return null;
		}
	}

	/**
	 * Returns the proxy settings of the PAC script for an URL, cached by domain.
	 *
	 * @param destinationUrl
	 *            the URL to connect to
	 * @return the settings like "PROXY host:8080" or "DIRECT" in order of preference, or null if
	 *         the script returned no text; cached lists are unmodifiable
	 * @throws Exception
	 *             if the script is invalid or its execution fails
	 */
	public List<String> discoverProxySettings(final String destinationUrl) throws Exception {
		return discoverProxySettings(destinationUrl, null);
	}

	/**
	 * Returns the proxy settings of the PAC script for an URL.
	 *
	 * @param destinationUrl
	 *            the URL to connect to
	 * @param cacheType
	 *            the caching, null for {@link CacheType#CacheByDomain}
	 * @return the settings like "PROXY host:8080" or "DIRECT" in order of preference, or null if
	 *         the script returned no text; cached lists are unmodifiable
	 * @throws Exception
	 *             if the script is invalid or its execution fails
	 */
	public List<String> discoverProxySettings(final String destinationUrl, final CacheType cacheType) throws Exception {
		if (cacheType == CacheType.None) {
			return discoverProxySettingsInternal(destinationUrl);
		} else if (cacheType == CacheType.CacheByFullUrl) {
			synchronized (pacProxyCache) {
				if (pacProxyCache.containsKey(destinationUrl)) {
					return pacProxyCache.get(destinationUrl);
				} else {
					// Unmodifiable, because the cached list is returned to every caller
					final List<String> result = unmodifiableOrNull(discoverProxySettingsInternal(destinationUrl));
					pacProxyCache.put(destinationUrl, result);
					return result;
				}
			}
		} else {
			final String domain = getDomainFromUrl(destinationUrl);
			synchronized (pacProxyCache) {
				if (pacProxyCache.containsKey(domain)) {
					return pacProxyCache.get(domain);
				} else {
					// Unmodifiable, because the cached list is returned to every caller
					final List<String> result = unmodifiableOrNull(discoverProxySettingsInternal(destinationUrl));
					pacProxyCache.put(domain, result);
					return result;
				}
			}
		}
	}

	/**
	 * Returns the proxies of the PAC script for an URL, cached by domain, see
	 * {@link #discoverProxy(String, CacheType)}.
	 *
	 * @param destinationUrl
	 *            the URL to connect to
	 * @return the proxies in order of preference, null for a direct connection
	 * @throws Exception
	 *             if the script is invalid, its execution fails, or a proxy setting is not supported
	 */
	public List<Proxy> discoverProxy(final String destinationUrl) throws Exception {
		return discoverProxy(destinationUrl, null);
	}

	/**
	 * Returns the proxies of the PAC script for an URL. "PROXY", "HTTP" and "HTTPS" settings
	 * become HTTP proxies ({@link Proxy} cannot connect to a proxy by TLS), "SOCKS", "SOCKS4" and
	 * "SOCKS5" settings become SOCKS proxies, "DIRECT" becomes null.
	 *
	 * @param destinationUrl
	 *            the URL to connect to
	 * @param cacheType
	 *            the caching, null for {@link CacheType#CacheByDomain}
	 * @return the proxies in order of preference, null for a direct connection; a list with only
	 *         null if the script returned no text
	 * @throws Exception
	 *             if the script is invalid, its execution fails, or a proxy setting is not supported
	 */
	public List<Proxy> discoverProxy(final String destinationUrl, final CacheType cacheType) throws Exception {
		return discoverProxyInternal(destinationUrl, cacheType);
	}

	private List<String> discoverProxySettingsInternal(final String destinationUrl) {
		final String hostname = PacScriptParserUtilities.getHostnameFromRequestString(destinationUrl);
		final Map<String, Method> scriptMethods = getPacScriptMethods();
		final Context context = new Context();
		for (final Entry<String, Method> pacScriptMethodEntry : scriptMethods.entrySet()) {
			context.setDefinedMethod(pacScriptMethodEntry.getKey(), pacScriptMethodEntry.getValue());
		}

		final Method findProxyForUrlMethod = scriptMethods.get("FindProxyForURL");
		if (findProxyForUrlMethod == null) {
			throw new RuntimeException("PAC script does not define the required method 'FindProxyForURL'");
		}

		final List<Object> methodParameters = new ArrayList<>();
		methodParameters.add(destinationUrl);
		methodParameters.add(hostname);

		final Object pacScriptMethodReturnValue = findProxyForUrlMethod.executeMethod(context, methodParameters);
		if (pacScriptMethodReturnValue == null) {
			return null;
		} else if (pacScriptMethodReturnValue instanceof String) {
			// Empty entries, e.g. after a trailing ";", are ignored
			return Arrays.stream(((String) pacScriptMethodReturnValue).split(";")).map(x -> x.trim()).filter(x -> !x.isEmpty()).collect(Collectors.toList());
		} else {
			return null;
		}
	}

	private List<Proxy> discoverProxyInternal(final String destinationUrl, final CacheType cacheType) throws Exception {
		final List<String> proxySettings = discoverProxySettings(destinationUrl, cacheType);
		final List<Proxy> proxyConfigurations = new ArrayList<>();
		if (proxySettings != null) {
			for (final String proxyConfigurationString : proxySettings) {
				// Proxy types are case insensitive and separated from the address by any whitespace, e.g. "PROXY  host:8080"
				final String[] parts = proxyConfigurationString.trim().split("\\s+", 2);
				final String proxyType = parts[0].toUpperCase(Locale.ROOT);
				if ("DIRECT".equals(proxyType)) {
					proxyConfigurations.add(null);
				} else if (parts.length < 2) {
					throw new RuntimeException("Missing proxy address in proxy configuration: " + proxyConfigurationString);
				} else if ("PROXY".equals(proxyType) || "HTTP".equals(proxyType) || "HTTPS".equals(proxyType)) {
					proxyConfigurations.add(new Proxy(Proxy.Type.HTTP, parseProxyAddress(parts[1].trim(), "HTTPS".equals(proxyType) ? 443 : 80)));
				} else if ("SOCKS".equals(proxyType) || "SOCKS4".equals(proxyType) || "SOCKS5".equals(proxyType)) {
					proxyConfigurations.add(new Proxy(Proxy.Type.SOCKS, parseProxyAddress(parts[1].trim(), 1080)));
				} else {
					throw new RuntimeException("Unsupported proxy configuration type: " + proxyConfigurationString);
				}
			}
		} else {
			proxyConfigurations.add(null);
		}
		return proxyConfigurations;
	}

	/**
	 * Returns the parsed methods of the PAC script, parsing it on first use. Synchronized, because
	 * a parser may be shared by multiple threads.
	 *
	 * @return the methods by name
	 */
	private synchronized Map<String, Method> getPacScriptMethods() {
		if (pacScriptMethods == null) {
			pacScriptMethods = parsePacScript();
		}
		return pacScriptMethods;
	}

	/**
	 * Returns the domain of an URL for caching: the host in lower case without "www.".
	 *
	 * @param url
	 *            the URL, also without protocol like "example.com/path"
	 * @return the domain, or null if it cannot be determined
	 * @throws Exception
	 *             never, declared for compatibility
	 */
	public static String getDomainFromUrl(final String url) throws Exception {
		String domain = null;
		try {
			domain = new URI(url).getHost();
		} catch (@SuppressWarnings("unused") final URISyntaxException e) {
			// Use the host name of the request string
		}
		if (domain == null) {
			// URL without protocol like "example.com/path"
			domain = PacScriptParserUtilities.getHostnameFromRequestString(url);
		}
		if (domain == null) {
			return null;
		}
		domain = domain.toLowerCase(Locale.ROOT);
		return domain.startsWith("www.") ? domain.substring(4) : domain;
	}

	/**
	 * Parses a proxy address like "host:8080", "host" or "[::1]:8080".
	 *
	 * @param address
	 *            the address
	 * @param defaultPort
	 *            the port if none is given
	 * @return the socket address
	 */
	private static InetSocketAddress parseProxyAddress(final String address, final int defaultPort) {
		String proxyHost = address;
		String proxyPortString = null;
		if (proxyHost.startsWith("[")) {
			// Bracketed IPv6 address
			final int closingBracketIndex = proxyHost.indexOf(']');
			if (closingBracketIndex < 0) {
				throw new RuntimeException("Invalid proxy address: " + address);
			}
			if (proxyHost.length() > closingBracketIndex + 1 && proxyHost.charAt(closingBracketIndex + 1) == ':') {
				proxyPortString = proxyHost.substring(closingBracketIndex + 2);
			}
			proxyHost = proxyHost.substring(1, closingBracketIndex);
		} else if (proxyHost.contains(":")) {
			proxyPortString = proxyHost.substring(proxyHost.lastIndexOf(':') + 1);
			proxyHost = proxyHost.substring(0, proxyHost.lastIndexOf(':'));
		}
		int proxyPort = defaultPort;
		if (proxyPortString != null) {
			try {
				proxyPort = Integer.parseInt(proxyPortString);
			} catch (@SuppressWarnings("unused") final NumberFormatException e) {
				throw new RuntimeException("Invalid port number for proxy url '" + proxyHost + "': " + proxyPortString);
			}
		}
		return new InetSocketAddress(proxyHost, proxyPort);
	}

	private static List<String> unmodifiableOrNull(final List<String> list) {
		return list == null ? null : Collections.unmodifiableList(list);
	}

	/**
	 * Returns the parsed functions of the script as text, FindProxyForURL first.
	 */
	@Override
	public String toString() {
		final Map<String, Method> scriptMethods = getPacScriptMethods();
		String returnValue = "";
		if (scriptMethods.containsKey("FindProxyForURL")) {
			returnValue += scriptMethods.get("FindProxyForURL").toString() + "\n";
		}
		for (final Entry<String, Method> method : scriptMethods.entrySet()) {
			if (!("FindProxyForURL").equals(method.getKey())) {
				returnValue += method.getValue().toString() + "\n";
			}
		}
		return returnValue;
	}
}
