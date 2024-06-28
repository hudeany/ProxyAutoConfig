# ProxyAutoConfig
Proxy-Auto-Config Parser

How to:
```
public static void main(String[] args) {
	try {
		String pacUrlString = PacScriptParser.findPacFileUrlByWpad();
		PacScriptParser pacScriptParser = new PacScriptParser(new URL(pacUrlString));
		Proxy proxyGoogle = pacScriptParser.discoverProxy("https://google.de").get(0)
		HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection(proxy);
		httpURLConnection.connect();
	} catch (Exception e) {
		e.printStackTrace();
	}
}
```
