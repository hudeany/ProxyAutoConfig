function FindProxyForURL(url, host) {
	test = 0;
	for (var i=0; i<5; i++) {
		test++;
	}
	
	for (var i=0; i<3; i = i + 1) test++;
	
	for (var i=0; i<5; i++) {
		if (test == 8) {
			test = test + 1;
			continue;
		} else {
			break;
		}
	}
	
	while (true) {
		if (test == 9) {
			test = test + 1;
			continue;
		} else {
			break;
		}
	}

	if (test==10) {
		return "DIRECT";
	} else {
		return "PROXY proxy:80";
	}
}
