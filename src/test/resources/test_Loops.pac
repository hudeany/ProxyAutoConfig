function FindProxyForURL(url, host) {
	test = 0;
	for (var i=0; i<5; i++) {
		test++;
	}
	
	for (var i=0; i<5; i++) test++;

	if (test==10) {
		return "DIRECT";
	} else {
		return "PROXY proxy:80";
	}
}
