package de.soderer.pac;

import de.soderer.pac.utilities.ProxyConfiguration;

/**
 * Test helper, which is started as separate process with a controlled environment by {@link ProxyConfigurationTest}:
 * prints the proxy determined by {@link ProxyConfiguration#getEnvironmentProxy(String)} for each URL given as argument, one line per URL.
 */
public final class EnvironmentProxyProbe {
	private EnvironmentProxyProbe() {
		// Utility class
	}

	/**
	 * Prints "url=proxy URL" or "url=DIRECT" for each argument.
	 *
	 * @param urls the URLs to check
	 * @throws Exception if the proxy cannot be determined
	 */
	public static void main(final String[] urls) throws Exception {
		for (final String url : urls) {
			final String proxyUrl = ProxyConfiguration.getProxyURL(ProxyConfiguration.getEnvironmentProxy(url));
			System.out.println(url + "=" + (proxyUrl == null ? "DIRECT" : proxyUrl));
		}
	}
}
