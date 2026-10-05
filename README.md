# ProxyAutoConfig for Java

[![Maven Central](https://img.shields.io/maven-central/v/de.soderer/proxyautoconfig)](https://central.sonatype.com/artifact/de.soderer/proxyautoconfig)

A lightweight Java library to find the right proxy for an URL using **PAC files** (Proxy Auto-Config), without a JavaScript engine and without any external dependencies.

## Features

- **PAC interpreter** in pure Java for the JavaScript subset used in PAC files: functions, `var`/`let`/`const`, `if`/`else`, `for` and `while` loops, arrays, string and arithmetic operators
- **All standard PAC functions** like `isPlainHostName`, `dnsDomainIs`, `isInNet`, `shExpMatch`, `weekdayRange` and `timeRange`, plus the IPv6 extensions like `isInNetEx`
- **All proxy types**: `PROXY`, `HTTP`, `HTTPS`, `SOCKS`, `SOCKS4`, `SOCKS5` and `DIRECT`, converted to `java.net.Proxy`
- **PAC file discovery** by WPAD (Web Proxy Auto-Discovery)
- **One API for all proxy sources**: PAC file, WPAD, a fixed proxy, JVM system properties or environment variables (`HTTP_PROXY`, `NO_PROXY`)
- **Caching** of results and of the downloaded PAC file, thread-safe
- **Protected execution**: limits for script size, run time, loop steps and recursion depth, so a faulty PAC file cannot hang the application

## Contents

- [Installation](#installation)
- [Quick start](#quick-start)
- [Proxy configuration from different sources](#proxy-configuration-from-different-sources)
- [PAC files from a URL or by WPAD](#pac-files-from-a-url-or-by-wpad)
- [Caching](#caching)
- [Supported PAC functions](#supported-pac-functions)
- [Limitations](#limitations)

## Installation

The library is available on Maven Central. Replace `VERSION` with the version shown in the badge above.

**Maven**

```xml
<dependency>
	<groupId>de.soderer</groupId>
	<artifactId>proxyautoconfig</artifactId>
	<version>VERSION</version>
</dependency>
```

**Gradle**

```groovy
implementation "de.soderer:proxyautoconfig:VERSION"
```

**Without a build tool**, download the jar from the [GitHub releases](https://github.com/hudeany/ProxyAutoConfig/releases).

## Quick start

```java
final String pacScript = """
		function FindProxyForURL(url, host) {
			// Local hosts and the intranet are reached directly
			if (isPlainHostName(host) || dnsDomainIs(host, ".intranet.example.com")) {
				return "DIRECT";
			}
			// Internal networks
			if (isInNet(host, "10.0.0.0", "255.0.0.0")) {
				return "DIRECT";
			}
			// Downloads by a separate proxy
			if (shExpMatch(url, "*/downloads/*")) {
				return "PROXY download-proxy.example.com:3128";
			}
			return "PROXY proxy.example.com:8080; SOCKS socks.example.com:1080; DIRECT";
		}
		""";

final PacScriptParser parser = new PacScriptParser(pacScript);

// The settings as returned by the PAC script, in order of preference
System.out.println(parser.discoverProxySettings("https://www.example.org/index.html"));
// [PROXY proxy.example.com:8080, SOCKS socks.example.com:1080, DIRECT]

// The same as java.net.Proxy objects, null stands for DIRECT
final List<Proxy> proxies = parser.discoverProxy("https://www.example.org/index.html");
final Proxy proxy = proxies.get(0);

final URL url = URI.create("https://www.example.org/index.html").toURL();
final HttpURLConnection connection = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
```

## Proxy configuration from different sources

`ProxyConfiguration` gives the proxy for an URL, independent of where the proxy settings come from. This way an application can let its users choose the source in its settings:

```java
final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(ProxyConfigurationType.PACURL, "https://intranet.example.com/proxy.pac")
	.withPacScriptCacheMaxAge(60 * 60 * 1000); // reload the PAC file after one hour

final Proxy proxy = proxyConfiguration.getProxy("https://www.example.org");     // Proxy.NO_PROXY for DIRECT
final String proxyUrl = proxyConfiguration.getProxyURL("https://www.example.org"); // e.g. "http://proxy.example.com:8080"
```

| Type | Source of the proxy |
|---|---|
| `None` | No proxy, always a direct connection |
| `System` | JVM system properties `http.proxyHost`, `https.proxyHost`, `http.nonProxyHosts` |
| `Environment` | Environment variables `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` |
| `ProxyURL` | A fixed proxy like `"http://proxy.example.com:8080"` |
| `PACURL` | A PAC file downloaded from a URL |
| `WPAD` | A PAC file discovered by WPAD |

The type can also be read from a configuration text: `ProxyConfigurationType.getFromString("pac-url")` returns `PACURL`.

`ProxyConfiguration` and `java.net.Proxy` are imported from `de.soderer.pac.utilities` and `java.net`; the enum is nested: `import de.soderer.pac.utilities.ProxyConfiguration.ProxyConfigurationType;`.

## PAC files from a URL or by WPAD

```java
// PAC file from a known URL (downloaded once, max. 5 MB)
final PacScriptParser parser = new PacScriptParser(URI.create("https://intranet.example.com/proxy.pac").toURL());

// PAC file discovered by WPAD, null if none was found
final String pacUrl = PacScriptParser.findPacFileUrlByWpad();
```

> **Security note:** WPAD is a well-known attack vector. Any device in the local network can answer WPAD requests and serve a PAC file that redirects traffic through its own proxy. Therefore only the HTTPS candidate is tried by default. Plain HTTP discovery (`http://wpad.<domain>/wpad.dat`) must be enabled explicitly by `findPacFileUrlByWpad(true)` or `ProxyConfiguration.setAllowInsecureHttpWpad(true)`, and should only be used in trusted networks.

## Caching

Executing a PAC script for every request would be slow, so results are cached in a size-bounded LRU cache (1000 entries by default, configurable in the constructor):

| `CacheType` | Behavior |
|---|---|
| `CacheByDomain` (default) | One result per domain. Fast, but wrong for PAC scripts that check the URL path |
| `CacheByFullUrl` | One result per full URL |
| `None` | The script is executed for every call |

```java
parser.discoverProxySettings("https://files.example.org/downloads/setup.zip", PacScriptParser.CacheType.CacheByFullUrl);
// [PROXY download-proxy.example.com:3128]
```

## Supported PAC functions

| Function | Purpose |
|---|---|
| `isPlainHostName(host)` | Host name without domain |
| `dnsDomainIs(host, domain)` | Host belongs to a domain |
| `localHostOrDomainIs(host, hostdom)` | Host is the given host, also unqualified |
| `isResolvable(host)`, `dnsResolve(host)` | DNS lookup |
| `isInNet(host, pattern, mask)` | Host is in an IPv4 network |
| `myIpAddress()` | IPv4 address of this machine |
| `dnsDomainLevels(host)` | Number of dots in the host name |
| `shExpMatch(str, shexp)` | Shell expression match with `*` and `?` |
| `weekdayRange(...)`, `dateRange(...)`, `timeRange(...)` | Day of the week, date and time conditions, optionally in GMT |
| `isResolvableEx(host)`, `dnsResolveEx(host)`, `isInNetEx(ip, prefix)`, `myIpAddressEx()`, `sortIpAddressList(list)` | IPv6 extensions |

## Limitations

This is not a complete JavaScript interpreter, but it covers everything needed by real-world PAC files:

- The PAC file must consist of function definitions only, there is no code outside of functions.
- Objects, regular expressions and the JavaScript standard library (e.g. `String.prototype` methods) are not supported.
- `HTTPS` proxies are connected by plain HTTP, because `java.net.Proxy` cannot connect to a proxy by TLS.
- A script execution is stopped after 5 seconds, 2,000,000 loop steps or a call depth of 200, and throws a `PacScriptExecutionLimitException`.
