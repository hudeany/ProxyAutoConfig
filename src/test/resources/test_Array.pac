function FindProxyForURL(url, host) {
  testArray = [1, 1+1, 3];
  if (testArray[get_2() - 1] == 2) {
    return "DIRECT";
  } else {
    return "PROXY proxy:80";
  }
}

function get_2() {
  return 2;
}
