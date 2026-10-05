package de.soderer.pac.utilities;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * The predefined functions available to PAC scripts, as defined by Netscape and extended by
 * Microsoft (the "Ex" functions for IPv6).
 */
public class PacScriptMethods {
	/**
	 * Utility class, not to be instantiated.
	 */
	private PacScriptMethods() {
	}

	private final static String GMT = "GMT";
	private final static List<String> WEEKDAYS_SHORT = Collections
			.unmodifiableList(Arrays.asList("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"));
	private final static List<String> MONTH_SHORT = Collections.unmodifiableList(
			Arrays.asList("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"));

	/**
	 * Checks for a host name without domain, e.g. "www".
	 *
	 * @param host
	 *            the host name
	 * @return true, if the host name contains no dot
	 */
	public static boolean isPlainHostName(final String host) {
		return !host.contains(".");
	}

	/**
	 * Checks whether a host belongs to a domain, ignoring case.
	 *
	 * @param host
	 *            the host name
	 * @param domain
	 *            the domain, e.g. ".example.com"
	 * @return true, if the host name ends with the domain
	 */
	public static boolean dnsDomainIs(final String host, final String domain) {
		// Host names are case insensitive
		return host.toLowerCase(Locale.ROOT).endsWith(domain.toLowerCase(Locale.ROOT));
	}

	/**
	 * Checks whether a host is the given fully qualified host, or its unqualified name.
	 *
	 * @param host
	 *            the host name, e.g. "www" or "www.example.com"
	 * @param domain
	 *            the fully qualified host name, e.g. "www.example.com"
	 * @return true, if the host matches exactly or is the first label of the fully qualified name
	 */
	public static boolean localHostOrDomainIs(final String host, final String domain) {
		// Exact match, or an unqualified host name matching the first label of the domain ("w" must not match "www.example.com")
		return host.equalsIgnoreCase(domain)
				|| (!host.contains(".") && domain.toLowerCase(Locale.ROOT).startsWith(host.toLowerCase(Locale.ROOT) + "."));
	}

	/**
	 * Checks whether a host name can be resolved by DNS.
	 *
	 * @param host
	 *            the host name
	 * @return true, if the host name can be resolved
	 */
	public static boolean isResolvable(final String host) {
		try {
			InetAddress.getByName(host).getHostAddress();
			return true;
		} catch (@SuppressWarnings("unused") final UnknownHostException e) {
			return false;
		}
	}

	/**
	 * Checks whether a host is in an IPv4 network. The host name is resolved by DNS.
	 *
	 * @param host
	 *            the host name or IP address
	 * @param pattern
	 *            the network address, e.g. "10.0.0.0"
	 * @param mask
	 *            the network mask, e.g. "255.0.0.0"
	 * @return true, if the host is in the network; false if it cannot be resolved
	 */
	public static boolean isInNet(String host, final String pattern, final String mask) {
		try {
			host = dnsResolve(host);
			if (host == null || host.length() == 0) {
				return false;
			} else {
				final long lhost = parseIpAddressToLong(host);
				final long lpattern = parseIpAddressToLong(pattern);
				final long lmask = parseIpAddressToLong(mask);
				return (lhost & lmask) == (lpattern & lmask);
			}
		} catch (@SuppressWarnings("unused") final Exception e) {
			return false;
		}
	}

	/**
	 * Resolves a host name by DNS.
	 *
	 * @param host
	 *            the host name
	 * @return the IP address, or an empty text if the host cannot be resolved
	 */
	public static String dnsResolve(final String host) {
		try {
			return InetAddress.getByName(host).getHostAddress();
		} catch (@SuppressWarnings("unused") final UnknownHostException e) {
			return "";
		}
	}

	/**
	 * Returns the IPv4 address of this machine (the first address of an active non loopback network
	 * interface).
	 *
	 * @return the IP address, or an empty text if there is none
	 */
	public static String myIpAddress() {
		return getLocalAddressOfType(Inet4Address.class);
	}

	/**
	 * Returns the number of domain levels of a host name, i.e. its number of dots.
	 *
	 * @param host
	 *            the host name
	 * @return the number of dots, e.g. 2 for "www.example.com"
	 */
	public static int dnsDomainLevels(final String host) {
		int count = 0;
		int startPos = 0;
		while ((startPos = host.indexOf(".", startPos + 1)) > -1) {
			count++;
		}
		return count;
	}

	/**
	 * Checks a text against a shell expression: "*" matches any characters, "?" matches a single
	 * character, all other characters match literally.
	 *
	 * @param str
	 *            the text, e.g. an URL
	 * @param shexp
	 *            the shell expression, e.g. "*.example.com/*"
	 * @return true, if the whole text matches
	 */
	public static boolean shExpMatch(final String str, final String shexp) {
		// Shell expression: "*" matches any characters, "?" matches a single character
		final StringBuilder regex = new StringBuilder();
		final StringBuilder literal = new StringBuilder();
		for (final char c : shexp.toCharArray()) {
			if (c == '*' || c == '?') {
				if (literal.length() > 0) {
					regex.append(Pattern.quote(literal.toString()));
					literal.setLength(0);
				}
				regex.append(c == '*' ? ".*" : ".");
			} else {
				literal.append(c);
			}
		}
		if (literal.length() > 0) {
			regex.append(Pattern.quote(literal.toString()));
		}
		return Pattern.compile(regex.toString(), Pattern.DOTALL).matcher(str).matches();
	}

	/**
	 * Checks whether today is within a range of weekdays.
	 *
	 * @param weekdayStart
	 *            the first weekday: SUN, MON, TUE, WED, THU, FRI or SAT
	 * @param weekdayEnd
	 *            the last weekday, or null or "GMT" for a single day
	 * @param gmt
	 *            "GMT" to use GMT instead of local time, or null
	 * @return true, if today is within the range; ranges may wrap like "FRI" to "MON"
	 */
	public static boolean weekdayRange(final String weekdayStart, final String weekdayEnd, final String gmt) {
		// "GMT" may also be the second parameter, e.g. weekdayRange("MON", "GMT")
		final boolean useGmt = GMT.equalsIgnoreCase(gmt) || GMT.equalsIgnoreCase(weekdayEnd);
		final Calendar cal = getCurrentTime(useGmt);

		final int currentDay = cal.get(Calendar.DAY_OF_WEEK) - 1;
		final int from = indexOfCaseInsensitive(WEEKDAYS_SHORT, weekdayStart);
		int to = indexOfCaseInsensitive(WEEKDAYS_SHORT, weekdayEnd);
		if (to == -1) {
			to = from;
		}

		if (to < from) {
			return currentDay >= from || currentDay <= to;
		} else {
			return currentDay >= from && currentDay <= to;
		}
	}

	/**
	 * Checks whether today is within a date range. The parameters are interpreted by their values:
	 * numbers up to 31 are days, larger numbers are years, texts like "JAN" are months, and "GMT"
	 * selects GMT instead of local time. Missing parameters are null.
	 *
	 * @param dayStart
	 *            first parameter
	 * @param monthStart
	 *            second parameter, or null
	 * @param yearStart
	 *            third parameter, or null
	 * @param dayEnd
	 *            fourth parameter, or null
	 * @param monthEnd
	 *            fifth parameter, or null
	 * @param yearEnd
	 *            sixth parameter, or null
	 * @param gmt
	 *            seventh parameter, or null
	 * @return true, if today is within the range
	 */
	public static boolean dateRange(final Object dayStart, final Object monthStart, final Object yearStart,
			final Object dayEnd, final Object monthEnd, final Object yearEnd, final Object gmt) {
		final Map<String, Integer> params = new HashMap<>();
		parseDateParam(params, dayStart);
		parseDateParam(params, monthStart);
		parseDateParam(params, yearStart);
		parseDateParam(params, dayEnd);
		parseDateParam(params, monthEnd);
		parseDateParam(params, yearEnd);
		parseDateParam(params, gmt);

		final boolean useGmt = params.get("gmt") != null;
		final Calendar cal = getCurrentTime(useGmt);
		final Date current = cal.getTime();

		if (params.get("day1") != null) {
			cal.set(Calendar.DAY_OF_MONTH, params.get("day1"));
		}
		if (params.get("month1") != null) {
			cal.set(Calendar.MONTH, params.get("month1"));
		}
		if (params.get("year1") != null) {
			cal.set(Calendar.YEAR, params.get("year1"));
		}
		final Date from = cal.getTime();

		Date to;
		if (params.get("day2") != null) {
			cal.set(Calendar.DAY_OF_MONTH, params.get("day2"));
		}
		if (params.get("month2") != null) {
			cal.set(Calendar.MONTH, params.get("month2"));
		}
		if (params.get("year2") != null) {
			cal.set(Calendar.YEAR, params.get("year2"));
		}
		to = cal.getTime();

		if (to.before(from)) {
			cal.add(Calendar.MONTH, +1);
			to = cal.getTime();
		}

		if (to.before(from)) {
			cal.add(Calendar.YEAR, +1);
			cal.add(Calendar.MONTH, -1);
			to = cal.getTime();
		}

		return current.compareTo(from) >= 0 && current.compareTo(to) <= 0;
	}

	/**
	 * Checks whether the current time is within a time range. Supported forms: (hour),
	 * (hour1, hour2), (hour1, min1, hour2, min2) and (hour1, min1, sec1, hour2, min2, sec2), each
	 * optionally followed by "GMT". Missing parameters are null. Ranges over midnight are supported.
	 *
	 * @param hour1
	 *            first parameter
	 * @param min1
	 *            second parameter, or null
	 * @param sec1
	 *            third parameter, or null
	 * @param hour2
	 *            fourth parameter, or null
	 * @param min2
	 *            fifth parameter, or null
	 * @param sec2
	 *            sixth parameter, or null
	 * @param gmt
	 *            seventh parameter, or null
	 * @return true, if the current time is within the range
	 */
	public static boolean timeRange(final Object hour1, final Object min1, final Object sec1, final Object hour2,
			final Object min2, final Object sec2, final Object gmt) {
		final boolean useGmt = GMT.equalsIgnoreCase(String.valueOf(min1)) || GMT.equalsIgnoreCase(String.valueOf(sec1))
				|| GMT.equalsIgnoreCase(String.valueOf(min2)) || GMT.equalsIgnoreCase(String.valueOf(gmt));

		final Calendar cal = getCurrentTime(useGmt);
		cal.set(Calendar.MILLISECOND, 0);
		final Date current = cal.getTime();
		Date from;
		Date to;
		if (sec2 instanceof Number) {
			cal.set(Calendar.HOUR_OF_DAY, ((Number) hour1).intValue());
			cal.set(Calendar.MINUTE, ((Number) min1).intValue());
			cal.set(Calendar.SECOND, ((Number) sec1).intValue());
			from = cal.getTime();

			cal.set(Calendar.HOUR_OF_DAY, ((Number) hour2).intValue());
			cal.set(Calendar.MINUTE, ((Number) min2).intValue());
			cal.set(Calendar.SECOND, ((Number) sec2).intValue());
			to = cal.getTime();
		} else if (hour2 instanceof Number) {
			cal.set(Calendar.HOUR_OF_DAY, ((Number) hour1).intValue());
			cal.set(Calendar.MINUTE, ((Number) min1).intValue());
			cal.set(Calendar.SECOND, 0);
			from = cal.getTime();

			cal.set(Calendar.HOUR_OF_DAY, ((Number) sec1).intValue());
			cal.set(Calendar.MINUTE, ((Number) hour2).intValue());
			cal.set(Calendar.SECOND, 59);
			to = cal.getTime();
		} else if (min1 instanceof Number) {
			cal.set(Calendar.HOUR_OF_DAY, ((Number) hour1).intValue());
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			from = cal.getTime();

			cal.set(Calendar.HOUR_OF_DAY, ((Number) min1).intValue());
			cal.set(Calendar.MINUTE, 59);
			cal.set(Calendar.SECOND, 59);
			to = cal.getTime();
		} else {
			cal.set(Calendar.HOUR_OF_DAY, ((Number) hour1).intValue());
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			from = cal.getTime();

			cal.set(Calendar.HOUR_OF_DAY, ((Number) hour1).intValue());
			cal.set(Calendar.MINUTE, 59);
			cal.set(Calendar.SECOND, 59);
			to = cal.getTime();
		}

		if (to.before(from)) {
			// Range over midnight, e.g. 22:00 to 02:00
			return current.compareTo(from) >= 0 || current.compareTo(to) <= 0;
		}

		return current.compareTo(from) >= 0 && current.compareTo(to) <= 0;
	}

	/**
	 * Checks whether a host name can be resolved by DNS (IPv4 or IPv6).
	 *
	 * @param host
	 *            the host name
	 * @return true, if the host name can be resolved
	 */
	public static boolean isResolvableEx(final String host) {
		return isResolvable(host);
	}

	/**
	 * Checks whether an IP address is in a network given by prefix, for IPv4 and IPv6.
	 *
	 * @param ipAddress
	 *            the IP address or host name
	 * @param ipPrefix
	 *            the network like "10.0.0.0/8" or "2001:db8::/32"; without prefix length the whole
	 *            address must match
	 * @return true, if the address is in the network; false for different IP versions
	 */
	public static boolean isInNetEx(final String ipAddress, final String ipPrefix) {
		try {
			final byte[] addressBytes = InetAddress.getByName(ipAddress).getAddress();
			final byte[] prefixBytes;
			final int addressBits = addressBytes.length * 8;
			final int bitMaskLength;
			if (ipPrefix.contains("/")) {
				prefixBytes = InetAddress.getByName(ipPrefix.substring(0, ipPrefix.indexOf("/"))).getAddress();
				bitMaskLength = Integer.parseInt(ipPrefix.substring(ipPrefix.indexOf("/") + 1).trim());
			} else {
				// Without prefix length the whole address must match
				prefixBytes = InetAddress.getByName(ipPrefix).getAddress();
				bitMaskLength = addressBits;
			}
			if (prefixBytes.length != addressBytes.length || bitMaskLength < 0 || bitMaskLength > addressBits) {
				// IPv4 address and IPv6 prefix or the other way round, or invalid prefix length
				return false;
			}
			// Compare the leading prefix bits of both addresses
			final BigInteger addressPrefixBits = new BigInteger(1, addressBytes).shiftRight(addressBits - bitMaskLength);
			final BigInteger prefixPrefixBits = new BigInteger(1, prefixBytes).shiftRight(addressBits - bitMaskLength);
			return addressPrefixBits.equals(prefixPrefixBits);
		} catch (@SuppressWarnings("unused") final Exception e) {
			return false;
		}
	}

	/**
	 * Resolves a host name to all its IP addresses (IPv4 and IPv6).
	 *
	 * @param host
	 *            the host name
	 * @return the IP addresses separated by ";", or an empty text if the host cannot be resolved
	 */
	public static String dnsResolveEx(final String host) {
		final StringBuilder result = new StringBuilder();
		try {
			final InetAddress[] list = InetAddress.getAllByName(host);
			for (final InetAddress inetAddress : list) {
				if (result.length() > 0) {
					result.append(";");
				}
				result.append(inetAddress.getHostAddress());
			}
			return result.toString();
		} catch (@SuppressWarnings("unused") final UnknownHostException e) {
			return result.toString();
		}
	}

	/**
	 * Returns the IPv6 address of this machine (the first address of an active non loopback network
	 * interface).
	 *
	 * @return the IP address, or an empty text if there is none
	 */
	public static String myIpAddressEx() {
		return getLocalAddressOfType(Inet6Address.class);
	}

	/**
	 * Sorts IP addresses, IPv4 addresses before IPv6 addresses. Invalid entries are removed.
	 *
	 * @param ipAddressList
	 *            the IP addresses separated by ";"
	 * @return the sorted IP addresses separated by ";"
	 */
	public static String sortIpAddressList(final String ipAddressList) {
		if (ipAddressList == null || ipAddressList.trim().length() == 0) {
			return "";
		}
		final List<InetAddress> parsedAddresses = new ArrayList<>();
		for (final String ip : ipAddressList.split(";")) {
			try {
				parsedAddresses.add(InetAddress.getByName(ip));
			} catch (@SuppressWarnings("unused") final UnknownHostException e) {
				// Do nothing
			}
		}

		parsedAddresses.sort((a, b) -> {
			final byte[] ba = a.getAddress();
			final byte[] bb = b.getAddress();
			if (ba.length != bb.length) {
				// IPv4 (4 Bytes) vor IPv6 (16 Bytes)
				return Integer.compare(ba.length, bb.length);
			}
			for (int i = 0; i < ba.length; i++) {
				final int diff = (ba[i] & 0xFF) - (bb[i] & 0xFF);
				if (diff != 0) {
					return diff;
				}
			}
			return 0;
		});

		final StringBuilder result = new StringBuilder();
		for (final InetAddress address : parsedAddresses) {
			if (result.length() > 0) {
				result.append(";");
			}
			result.append(address.getHostAddress());
		}
		return result.toString();
	}

	/**
	 * Returns the version of the PAC functions.
	 *
	 * @return "1.0"
	 */
	public static String getClientVersion() {
		return "1.0";
	}

	private static long parseIpAddressToLong(final String address) {
		long result = 0;
		long shift = 24;
		for (final String part : address.split("\\.")) {
			final long lpart = Long.parseLong(part);

			result |= lpart << shift;
			shift -= 8;
		}
		return result;
	}

	private static String getLocalAddressOfType(final Class<? extends InetAddress> cl) {
		try {
			final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				final NetworkInterface current = interfaces.nextElement();
				if (!current.isUp() || current.isLoopback() || current.isVirtual()) {
					continue;
				}
				final Enumeration<InetAddress> addresses = current.getInetAddresses();
				while (addresses.hasMoreElements()) {
					final InetAddress adr = addresses.nextElement();
					if (cl.isInstance(adr)) {
						return adr.getHostAddress();
					}
				}
			}
			return "";
		} catch (@SuppressWarnings("unused") final IOException e) {
			return "";
		}
	}

	private static Calendar getCurrentTime(final boolean useGmt) {
		return Calendar.getInstance(useGmt ? TimeZone.getTimeZone(GMT) : TimeZone.getDefault());
	}

	private static void parseDateParam(final Map<String, Integer> params, final Object value) {
		if (value instanceof Number) {
			final int n = ((Number) value).intValue();
			if (n <= 31) {
				// Its a day
				if (params.get("day1") == null) {
					params.put("day1", n);
				} else {
					params.put("day2", n);
				}
			} else {
				// Its a year
				if (params.get("year1") == null) {
					params.put("year1", n);
				} else {
					params.put("year2", n);
				}
			}
		}

		if (value instanceof String) {
			final int n = MONTH_SHORT.indexOf(((String) value).toUpperCase(Locale.ENGLISH));
			if (n > -1) {
				// Its a month
				if (params.get("month1") == null) {
					params.put("month1", n);
				} else {
					params.put("month2", n);
				}
			}
		}

		if (GMT.equalsIgnoreCase(String.valueOf(value))) {
			params.put("gmt", 1);
		}
	}

	private static int indexOfCaseInsensitive(final List<String> data, final String item) {
		for (int i = 0; i < data.size(); i++) {
			final String dataItem = data.get(i);
			if (dataItem == item) {
				return i;
			} else if (dataItem != null && dataItem.equalsIgnoreCase(item)) {
				return i;
			}
		}
		return -1;
	}
}
