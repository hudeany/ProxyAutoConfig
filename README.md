# ProxyAutoConfig

[![Maven Central](https://img.shields.io/maven-central/v/de.soderer/proxyautoconfig)](https://central.sonatype.com/artifact/de.soderer/proxyautoconfig)

Proxy-Auto-Config Parser and Interpreter

A simple parser and interpreter for Javascript PAC (Proxy-Auto-Config) files in pure JAVA.

This is far from being a perfect Javascript interpreter, but it supports all needed functionality for using PAC files to detect the right proxy server settings for a given URL to call via HTTP proxy.

## Usage

The library is available on Maven Central. Replace `VERSION` with the version shown in the badge above.

Maven:

```xml
<dependency>
	<groupId>de.soderer</groupId>
	<artifactId>proxyautoconfig</artifactId>
	<version>VERSION</version>
</dependency>
```

Gradle:

```groovy
implementation "de.soderer:proxyautoconfig:VERSION"
```

Without a build tool, the jar can be downloaded from the [GitHub releases](https://github.com/hudeany/ProxyAutoConfig/releases).

## How to
```
public static void main(String[] args) {
	try {
		String urlToCall = "https://example.com";
		String pacUrlString = null; // Configure your PAC file url, if known

		if (pacUrlString == null) {
			// Try to detect PAC file url by WPAD (Web Proxy Autodiscovery Protocol) standard
			pacUrlString = PacScriptParser.findPacFileUrlByWpad();
		}

		PacScriptParser pacScriptParser;
		if (pacUrlString != null) {
 			pacScriptParser = new PacScriptParser(new URL(pacUrlString));
		} else {
			// Use my own PAC data
			String pacData = 
				"function FindProxyForURL(url, host) {"
				+ "if (isPlainHostName(host)) {"
				+ "return \"DIRECT\";"
				+ "} else {"
				+ "return \"PROXY proxy:80\";"
				+ "}"
				+ "}";
 			pacScriptParser = new PacScriptParser(pacData);
		}

		List<Proxy> multipleAllowedProxySettingsForThisUrl = pacScriptParser.discoverProxy(urlToCall);
		Proxy proxy = multipleAllowedProxySettingsForThisUrl.get(0);
		HttpURLConnection httpURLConnection;
		if (proxy == null) {
			// DIRECT connection without proxy
			httpURLConnection = (HttpURLConnection) new URL(urlToCall).openConnection();
		} else {
			httpURLConnection = (HttpURLConnection) new URL(urlToCall).openConnection(proxy);
		}
		httpURLConnection.connect();
	} catch (Exception e) {
		e.printStackTrace();
	}
}
```
