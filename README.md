# ProxyAutoConfig
Proxy-Auto-Config Parser

How to:
```
public static void main(String[] args) {
		try {
			String pacUrlString = PacScriptParser.findPacFileUrlByWpad();
			System.out.println("Found PAC URL: " + pacUrlString);
			if (pacUrlString != null) {
				PacScriptParser pacScriptParser = new PacScriptParser(new URL(pacUrlString));
				Proxy proxyGoogle = pacScriptParser.discoverProxy("https://google.de").get(0);
				System.out.println("google.de (Proxy " + proxyGoogle + "): " + ping("https://google.de", proxyGoogle));
				Proxy proxySoderer = pacScriptParser.discoverProxy("https://soderer.de/index.php?download=Versions.json").get(0);
				System.out.println("soderer.de (Proxy " + proxySoderer + "): " + ping("https://soderer.de/index.php?download=Versions.json", proxySoderer));
			} else {
				System.out.println("google.de: " + ping("https://google.de", null));
				
				System.out.println("soderer.de: " + ping("https://soderer.de/index.php?download=Versions.json", null));
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
```
