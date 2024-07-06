# ProxyAutoConfig
Proxy-Auto-Config Parser and Interpreter

A simple parser and interpreter for Javascript PAC (Proxy-Auto-Config) files in pure JAVA.
PACs are basically JavaScript, but this interpreter does not fully support JavaScript.
Especially JavaScript classes are not supported. But those are not utilized in PAC scripts.

This is far from being a perfect Javascript interpreter, but it supports all needed functionality for using PAC files to detect the right proxy server settings for a given URL to call via HTTP proxy.

How to:
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
