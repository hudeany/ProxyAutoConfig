package de.soderer.pac;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import de.soderer.pac.utilities.Context;
import de.soderer.pac.utilities.Method;
import de.soderer.pac.utilities.PacScriptParserUtilities;

public class PacScriptParser {
	private static final int DEFAULT_MAX_PAC_PROXY_CACHE_ENTRIES = 1000;

	private String pacScriptData = null;
	private Map<String, Method> pacScriptMethods = null;

	private final Map<String, List<String>> pacProxyCache;

	/**
	 * Default: CacheByDomain
	 */
	public enum CacheType {
		None,
		CacheByDomain,
		CacheByFullUrl
	}

	public PacScriptParser(final URL pacUrl) {
		this(pacUrl, DEFAULT_MAX_PAC_PROXY_CACHE_ENTRIES);
	}

	public PacScriptParser(final URL pacUrl, final int maxPacProxyCacheEntries) {
		pacScriptData = PacScriptParserUtilities.readPacData(pacUrl);
		pacProxyCache = createBoundedCache(maxPacProxyCacheEntries);
	}

	public PacScriptParser(final String pacScriptData) throws Exception {
		this(pacScriptData, DEFAULT_MAX_PAC_PROXY_CACHE_ENTRIES);
	}

	public PacScriptParser(final String pacScriptData, final int maxPacProxyCacheEntries) throws Exception {
		if (pacScriptData.trim().toLowerCase().startsWith("http")) {
			this.pacScriptData = PacScriptParserUtilities.readPacData(new URL(pacScriptData.trim()));
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

	public Map<String, Method> parsePacScript() {
		final Map<String, Method> methodDefinitions = new HashMap<>();

		List<String> pacScriptTokens = PacScriptParserUtilities.tokenize(PacScriptParserUtilities.removeComments(pacScriptData));

		pacScriptTokens = PacScriptParserUtilities.replaceAliases(pacScriptTokens);

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
					final URLConnection pacConnection = new URL(pacUrlCandidate).openConnection();
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

	public List<String> discoverProxySettings(final String destinationUrl) throws Exception {
		return discoverProxySettings(destinationUrl, null);
	}

	public List<String> discoverProxySettings(final String destinationUrl, final CacheType cacheType) throws Exception {
		if (cacheType == CacheType.None) {
			return discoverProxySettingsInternal(destinationUrl);
		} else if (cacheType == CacheType.CacheByFullUrl) {
			synchronized (pacProxyCache) {
				if (pacProxyCache.containsKey(destinationUrl)) {
					return pacProxyCache.get(destinationUrl);
				} else {
					final List<String> result = discoverProxySettingsInternal(destinationUrl);
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
					final List<String> result = discoverProxySettingsInternal(destinationUrl);
					pacProxyCache.put(domain, result);
					return result;
				}
			}
		}
	}

	public List<Proxy> discoverProxy(final String destinationUrl) throws Exception {
		return discoverProxy(destinationUrl, null);
	}

	public List<Proxy> discoverProxy(final String destinationUrl, final CacheType cacheType) throws Exception {
		return discoverProxyInternal(destinationUrl, cacheType);
	}

	private List<String> discoverProxySettingsInternal(final String destinationUrl) {
		final String hostname = PacScriptParserUtilities.getHostnameFromRequestString(destinationUrl);
		if (pacScriptMethods == null) {
			pacScriptMethods = parsePacScript();
		}
		final Context context = new Context();
		for (final Entry<String, Method> pacScriptMethodEntry : pacScriptMethods.entrySet()) {
			context.setDefinedMethod(pacScriptMethodEntry.getKey(), pacScriptMethodEntry.getValue());
		}

		final Method findProxyForUrlMethod = pacScriptMethods.get("FindProxyForURL");
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
			return Arrays.stream(((String) pacScriptMethodReturnValue).split(";")).map(x -> x.trim()).collect(Collectors.toList());
		} else {
			return null;
		}
	}

	private List<Proxy> discoverProxyInternal(final String destinationUrl, final CacheType cacheType) throws Exception {
		final List<String> proxySettings = discoverProxySettings(destinationUrl, cacheType);
		final List<Proxy> proxyConfigurations = new ArrayList<>();
		if (proxySettings != null) {
			for (String proxyConfigurationString : proxySettings) {
				if ("DIRECT".equals(proxyConfigurationString)) {
					proxyConfigurations.add(null);
				} else if (proxyConfigurationString.startsWith("PROXY ")) {
					proxyConfigurationString = proxyConfigurationString.substring(6);
					String proxyHost;
					int proxyPort;
					if (proxyConfigurationString.contains(":")) {
						proxyHost = proxyConfigurationString.substring(0, proxyConfigurationString.indexOf(":"));
						try {
							proxyPort = Integer.parseInt(proxyConfigurationString.substring(proxyConfigurationString.indexOf(":") + 1));
						} catch (@SuppressWarnings("unused") final NumberFormatException e) {
							throw new RuntimeException("Invalid port number for proxy url '" + proxyHost + "': " + proxyConfigurationString.substring(proxyConfigurationString.indexOf(":") + 1));
						}
					} else {
						proxyHost = proxyConfigurationString;
						proxyPort = 80;
					}
					proxyConfigurations.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
				} else {
					throw new RuntimeException("Unsupported proxy configuration type: " + proxyConfigurationString);
				}
			}
		} else {
			proxyConfigurations.add(null);
		}
		return proxyConfigurations;
	}

	public static String getDomainFromUrl(final String url) throws Exception {
		final URI uri = new URI(url);
		final String domain = uri.getHost();
		return domain.startsWith("www.") ? domain.substring(4) : domain;
	}

	@Override
	public String toString() {
		if (pacScriptMethods == null) {
			pacScriptMethods = parsePacScript();
		}
		String returnValue = "";
		if (pacScriptMethods.containsKey("FindProxyForURL")) {
			returnValue += pacScriptMethods.get("FindProxyForURL").toString() + "\n";
		}
		for (final Entry<String, Method> method : pacScriptMethods.entrySet()) {
			if (!("FindProxyForURL").equals(method.getKey())) {
				returnValue += method.getValue().toString() + "\n";
			}
		}
		return returnValue;
	}
}
