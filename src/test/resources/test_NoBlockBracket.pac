function FindProxyForURL(url, host){
  if (isPlainHostName(host))
    return "DIRECT";
  else
    return "PROXY proxy:80";
}
