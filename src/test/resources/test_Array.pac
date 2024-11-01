function FindProxyForURL(url, host) {
  testArray = [1, 2, 3];
  if (testArray[0 + 1] == 2) {
    return "DIRECT";
  } else {
    return "PROXY proxy:80";
  }
}
