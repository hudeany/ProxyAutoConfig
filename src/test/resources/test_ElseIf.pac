function FindProxyForURL(url, host){
  if (localHostOrDomainIs(host, "other.com"))
    return "DIRECT";
  else if (isPlainHostName(host))
    return "PROXY proxyElseIf:80";
  else
    return "PROXY proxy:80";
}
