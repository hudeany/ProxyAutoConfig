# ProxyAutoConfig
Proxy-Auto-Config Parser and Interpreter

A simple parser and interpreter for Javascript PAC (Proxy-Auto-Config) files in pure JAVA.

This is far from being a perfect Javascript interpreter, but it supports all needed functionality for using PAC files to detect the right proxy server settings for a given URL to call via HTTP proxy.

How to:
```
public static void main(String[] args) {
	try {
		String urlToCall = "https://example.com";
		String pacUrlString = PacScriptParser.findPacFileUrlByWpad();
		PacScriptParser pacScriptParser = new PacScriptParser(new URL(pacUrlString));
		Proxy multipleAllowedProxySettingsForThisUrl = pacScriptParser.discoverProxy(urlToCall);
		HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection(multipleAllowedProxySettingsForThisUrl.get(0));
		httpURLConnection.connect();
	} catch (Exception e) {
		e.printStackTrace();
	}
}
```
