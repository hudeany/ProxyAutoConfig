function FindProxyForURL(url, host) {
  if (isInNet(host, "10.0.0.0", "255.255.0.0")) {return "DIRECT";}
  else if (add(1, 1 + 1) == 3)
  return "PROXY 10.0.0.1:8080";
}

function add(param1, param2) {
  return param1 + param2;
}
