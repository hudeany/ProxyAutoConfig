# ProxyAutoConfig
Proxy-Auto-Config Parser and Interpreter

A simple parser and interpreter for Javascript PAC (Proxy-Auto-Config) files in pure JAVA.

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

		Proxy multipleAllowedProxySettingsForThisUrl = pacScriptParser.discoverProxy(urlToCall);
		HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection(multipleAllowedProxySettingsForThisUrl.get(0));
		httpURLConnection.connect();
	} catch (Exception e) {
		e.printStackTrace();
	}
}
```
