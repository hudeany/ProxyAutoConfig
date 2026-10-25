package de.soderer.pac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import de.soderer.pac.utilities.PacScriptMethods;

/**
 * Tests of the PAC functions in {@link PacScriptMethods}.
 * Host names are given as IP addresses or invalid IPv6 literals ("[::zz]"), so no DNS lookup is needed.
 * The time functions are checked with ranges relative to the current time, a check is repeated if the hour changes during the check.
 */
@SuppressWarnings("static-method")
public class PacScriptMethodsExtendedTest {
	private static final String UNRESOLVABLE_HOST = "[::zz]";
	private static final List<String> WEEKDAYS = Arrays.asList("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT");

	private static Calendar now(final boolean gmt) {
		return Calendar.getInstance(gmt ? TimeZone.getTimeZone("GMT") : TimeZone.getDefault());
	}

	/**
	 * Evaluates a check, which depends on the current hour, again if the hour changed during the evaluation
	 */
	private static boolean stable(final Supplier<Boolean> check) {
		while (true) {
			final int hourBefore = now(false).get(Calendar.HOUR_OF_DAY);
			final boolean result = check.get();
			if (now(false).get(Calendar.HOUR_OF_DAY) == hourBefore) {
				return result;
			}
		}
	}

	@Test
	public void testUtilityClassConstructor() throws Exception {
		final Constructor<PacScriptMethods> constructor = PacScriptMethods.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		constructor.newInstance();
	}

	// ---- Host names ----

	@Test
	public void testIsPlainHostName() {
		assertTrue(PacScriptMethods.isPlainHostName("intranet"));
		assertFalse(PacScriptMethods.isPlainHostName("www.example.com"));
	}

	@Test
	public void testDnsDomainIs() {
		assertTrue(PacScriptMethods.dnsDomainIs("www.example.com", ".example.com"));
		assertFalse(PacScriptMethods.dnsDomainIs("www.example.org", ".example.com"));
	}

	@Test
	public void testLocalHostOrDomainIs() {
		assertTrue(PacScriptMethods.localHostOrDomainIs("www.example.com", "www.example.com"));
		assertTrue(PacScriptMethods.localHostOrDomainIs("WWW", "www.example.com"));
		assertFalse(PacScriptMethods.localHostOrDomainIs("www.example.org", "www.example.com"));
		assertFalse(PacScriptMethods.localHostOrDomainIs("home", "www.example.com"));
	}

	@Test
	public void testDnsDomainLevels() {
		assertEquals(0, PacScriptMethods.dnsDomainLevels("intranet"));
		assertEquals(1, PacScriptMethods.dnsDomainLevels("example.com"));
		assertEquals(2, PacScriptMethods.dnsDomainLevels("www.example.com"));
	}

	@Test
	public void testShExpMatch() {
		assertTrue(PacScriptMethods.shExpMatch("http://home.example.com/x", "*.example.com/*"));
		assertTrue(PacScriptMethods.shExpMatch("abc", "a?c"));
		assertTrue(PacScriptMethods.shExpMatch("", "*"));
		assertTrue(PacScriptMethods.shExpMatch("line1\nline2", "line1*"));
		assertFalse(PacScriptMethods.shExpMatch("abbc", "a?c"));
		// Regular expression characters are matched literally
		assertTrue(PacScriptMethods.shExpMatch("a+b.c", "a+b.c"));
		assertFalse(PacScriptMethods.shExpMatch("aab_c", "a+b.c"));
		assertTrue(PacScriptMethods.shExpMatch("[x]", "[x]"));
	}

	// ---- Resolving ----

	@Test
	public void testResolving() {
		assertTrue(PacScriptMethods.isResolvable("127.0.0.1"));
		assertTrue(PacScriptMethods.isResolvableEx("::1"));
		assertFalse(PacScriptMethods.isResolvable(UNRESOLVABLE_HOST));
		assertFalse(PacScriptMethods.isResolvableEx(UNRESOLVABLE_HOST));

		assertEquals("127.0.0.1", PacScriptMethods.dnsResolve("127.0.0.1"));
		assertEquals("", PacScriptMethods.dnsResolve(UNRESOLVABLE_HOST));
		assertEquals("", PacScriptMethods.dnsResolveEx(UNRESOLVABLE_HOST));
	}

	@Test
	public void testIsInNet() {
		assertTrue(PacScriptMethods.isInNet("10.1.2.3", "10.0.0.0", "255.0.0.0"));
		assertFalse(PacScriptMethods.isInNet("11.1.2.3", "10.0.0.0", "255.0.0.0"));
		assertTrue(PacScriptMethods.isInNet("192.168.1.1", "0.0.0.0", "0.0.0.0"));
		assertFalse(PacScriptMethods.isInNet(UNRESOLVABLE_HOST, "10.0.0.0", "255.0.0.0"));
		assertFalse(PacScriptMethods.isInNet("10.1.2.3", "10.0.0.0", "invalid.mask"));
	}

	@Test
	public void testIsInNetEx() {
		// Without prefix length the whole address must match
		assertTrue(PacScriptMethods.isInNetEx("10.0.0.1", "10.0.0.1"));
		assertFalse(PacScriptMethods.isInNetEx("10.0.0.1", "10.0.0.2"));
		assertTrue(PacScriptMethods.isInNetEx("10.0.0.1", "0.0.0.0/0"));
		assertFalse(PacScriptMethods.isInNetEx("10.0.0.1", "10.0.0.0/33"));
		assertFalse(PacScriptMethods.isInNetEx("10.0.0.1", "10.0.0.0/-1"));
		assertFalse(PacScriptMethods.isInNetEx("::1", "10.0.0.0/8"));
		assertFalse(PacScriptMethods.isInNetEx(UNRESOLVABLE_HOST, "10.0.0.0/8"));
	}

	@Test
	public void testMyIpAddresses() throws Exception {
		// The result depends on the network interfaces: empty or an address of the requested type
		final String ipv4Address = PacScriptMethods.myIpAddress();
		if (!ipv4Address.isEmpty()) {
			assertTrue(InetAddress.getByName(ipv4Address) instanceof Inet4Address, ipv4Address);
		}
		final String ipv6Address = PacScriptMethods.myIpAddressEx();
		if (!ipv6Address.isEmpty()) {
			assertTrue(InetAddress.getByName(ipv6Address) instanceof Inet6Address, ipv6Address);
		}
	}

	@Test
	public void testSortIpAddressList() {
		assertEquals("1.2.3.4;10.0.0.2;10.0.0.10;0:0:0:0:0:0:0:1", PacScriptMethods.sortIpAddressList("10.0.0.10;10.0.0.2;::1;" + UNRESOLVABLE_HOST + ";1.2.3.4"));
		assertEquals("10.0.0.1;10.0.0.1", PacScriptMethods.sortIpAddressList("10.0.0.1;10.0.0.1"));
		assertEquals("0:0:0:0:0:0:0:1;0:0:0:0:0:0:0:2", PacScriptMethods.sortIpAddressList("::2;::1"));
		assertEquals("", PacScriptMethods.sortIpAddressList(null));
		assertEquals("", PacScriptMethods.sortIpAddressList(" "));
	}

	@Test
	public void testGetClientVersion() {
		assertEquals("1.0", PacScriptMethods.getClientVersion());
	}

	// ---- Weekdays ----

	@Test
	public void testWeekdayRange() {
		assertTrue(PacScriptMethods.weekdayRange("SUN", "SAT", null));
		// Unknown day names match no day
		assertFalse(PacScriptMethods.weekdayRange("XYZ", null, null));

		boolean dayChanged;
		do {
			final int today = now(false).get(Calendar.DAY_OF_WEEK) - 1;
			final String todayName = WEEKDAYS.get(today);
			final String tomorrowName = WEEKDAYS.get((today + 1) % 7);
			final String yesterdayName = WEEKDAYS.get((today + 6) % 7);

			final boolean todayOnly = PacScriptMethods.weekdayRange(todayName.toLowerCase(Locale.ROOT), null, null);
			final boolean todayToToday = PacScriptMethods.weekdayRange(todayName, todayName, null);
			final boolean tomorrowOnly = PacScriptMethods.weekdayRange(tomorrowName, null, null);
			// Ranges over the end of the week
			final boolean wrappedIncludingToday = PacScriptMethods.weekdayRange(todayName, yesterdayName, null);
			final boolean wrappedExcludingToday = PacScriptMethods.weekdayRange(tomorrowName, yesterdayName, null);
			final boolean wrappedFromTomorrowToToday = PacScriptMethods.weekdayRange(tomorrowName, todayName, null);

			dayChanged = now(false).get(Calendar.DAY_OF_WEEK) - 1 != today;
			if (!dayChanged) {
				assertTrue(todayOnly);
				assertTrue(todayToToday);
				assertFalse(tomorrowOnly);
				assertTrue(wrappedIncludingToday);
				assertFalse(wrappedExcludingToday);
				assertTrue(wrappedFromTomorrowToToday);
			}
		} while (dayChanged);
	}

	@Test
	public void testWeekdayRangeGmt() {
		boolean dayChanged;
		do {
			final int todayGmt = now(true).get(Calendar.DAY_OF_WEEK) - 1;
			final String todayName = WEEKDAYS.get(todayGmt);
			final boolean gmtAsSecondParameter = PacScriptMethods.weekdayRange(todayName, "GMT", null);
			final boolean gmtAsThirdParameter = PacScriptMethods.weekdayRange(todayName, todayName, "gmt");
			dayChanged = now(true).get(Calendar.DAY_OF_WEEK) - 1 != todayGmt;
			if (!dayChanged) {
				assertTrue(gmtAsSecondParameter);
				assertTrue(gmtAsThirdParameter);
			}
		} while (dayChanged);
	}

	// ---- Dates ----

	@Test
	public void testDateRange() {
		final Calendar now = now(false);
		final int year = now.get(Calendar.YEAR);

		assertTrue(PacScriptMethods.dateRange(1, 31, null, null, null, null, null));
		assertFalse(PacScriptMethods.dateRange(year + 1, null, null, null, null, null, null));
		assertFalse(PacScriptMethods.dateRange(year - 1, null, null, null, null, null, null));
		assertTrue(PacScriptMethods.dateRange(year - 1, year + 1, null, null, null, null, null));
		assertTrue(PacScriptMethods.dateRange("JAN", "DEC", null, null, null, null, null));
		assertTrue(PacScriptMethods.dateRange(1, "JAN", year, 31, "DEC", year, null));
		assertFalse(PacScriptMethods.dateRange(1, "JAN", year + 1, 31, "DEC", year + 2, null));
		// Unknown texts are ignored, so there is no valid range
		assertFalse(PacScriptMethods.dateRange("unknown", null, null, null, null, null, null));
	}

	@Test
	public void testDateRangeOverYearEnd() {
		final int month = now(false).get(Calendar.MONTH);
		// The range from December to January does not include the months from February to November
		if (month != Calendar.DECEMBER && month != Calendar.JANUARY) {
			assertFalse(PacScriptMethods.dateRange("DEC", "JAN", null, null, null, null, null));
		}
	}

	@Test
	public void testDateRangeGmt() {
		final int yearGmt = now(true).get(Calendar.YEAR);
		assertTrue(PacScriptMethods.dateRange(yearGmt - 1, yearGmt + 1, "GMT", null, null, null, null));
		assertTrue(PacScriptMethods.dateRange(1, 31, "GMT", null, null, null, null));
	}

	// ---- Times ----

	@Test
	public void testTimeRangeAlwaysTrue() {
		assertTrue(PacScriptMethods.timeRange(0, 23, null, null, null, null, null));
		assertTrue(PacScriptMethods.timeRange(0, 0, 23, 59, null, null, null));
		assertTrue(PacScriptMethods.timeRange(0, 0, 0, 23, 59, 59, null));
	}

	@Test
	public void testTimeRangeRelativeToCurrentHour() {
		assertTrue(stable(() -> PacScriptMethods.timeRange(now(false).get(Calendar.HOUR_OF_DAY), null, null, null, null, null, null)));
		assertFalse(stable(() -> PacScriptMethods.timeRange((now(false).get(Calendar.HOUR_OF_DAY) + 1) % 24, null, null, null, null, null, null)));

		// From the next hour to the hour before over midnight excludes the current hour
		assertFalse(stable(() -> {
			final int hour = now(false).get(Calendar.HOUR_OF_DAY);
			return PacScriptMethods.timeRange((hour + 1) % 24, (hour + 23) % 24, null, null, null, null, null);
		}));
		// From the next hour to the current hour over midnight includes the current hour
		assertTrue(stable(() -> {
			final int hour = now(false).get(Calendar.HOUR_OF_DAY);
			return PacScriptMethods.timeRange((hour + 1) % 24, hour, null, null, null, null, null);
		}));
		assertFalse(stable(() -> {
			final int hour = now(false).get(Calendar.HOUR_OF_DAY);
			return PacScriptMethods.timeRange((hour + 1) % 24, 0, (hour + 23) % 24, 59, null, null, null);
		}));
		assertFalse(stable(() -> {
			final int hour = now(false).get(Calendar.HOUR_OF_DAY);
			return PacScriptMethods.timeRange((hour + 1) % 24, 0, 0, (hour + 23) % 24, 59, 59, null);
		}));
	}

	@Test
	public void testTimeRangeGmt() {
		assertTrue(PacScriptMethods.timeRange(0, 23, "GMT", null, null, null, null));
		assertTrue(PacScriptMethods.timeRange(0, 0, 23, 59, "GMT", null, null));
		assertTrue(PacScriptMethods.timeRange(0, 0, 0, 23, 59, 59, "GMT"));
		assertTrue(stable(() -> PacScriptMethods.timeRange(now(true).get(Calendar.HOUR_OF_DAY), "GMT", null, null, null, null, null)));
	}

	// ---- Functions called from scripts ----

	@Test
	public void testAllFunctionsCanBeCalledFromScripts() throws Exception {
		// Calls with several parameters cannot be used as array items, so the results are combined by "&&"
		final String[] calls = { "isPlainHostName(\"intranet\")", "dnsDomainIs(host, \".example.com\")", "localHostOrDomainIs(host, \"www.example.com\")",
				"isResolvable(\"127.0.0.1\")", "isInNet(\"10.1.1.1\", \"10.0.0.0\", \"255.0.0.0\")", "dnsDomainLevels(host) == 2", "shExpMatch(url, \"http://*\")",
				"weekdayRange(\"SUN\", \"SAT\")", "dateRange(1, 31)", "timeRange(0, 23)", "isResolvableEx(\"::1\")", "isInNetEx(\"10.0.0.1\", \"10.0.0.0/8\")",
				"dnsResolve(\"127.0.0.1\") == \"127.0.0.1\"", "dnsResolveEx(\"127.0.0.1\") == \"127.0.0.1\"", "sortIpAddressList(\"10.0.0.2;10.0.0.1\") == \"10.0.0.1;10.0.0.2\"",
				"getClientVersion() == \"1.0\"", "myIpAddress() == myIpAddress()", "myIpAddressEx() == myIpAddressEx()" };
		for (final String call : calls) {
			assertEquals(List.of("true"), new PacScriptParser("function FindProxyForURL(url, host) { return (" + call + ") + \"\"; }").discoverProxySettings("http://www.example.com/"), call);
		}
		final List<String> result = new PacScriptParser("function FindProxyForURL(url, host) { return (" + String.join(" && ", calls) + ") + \"\"; }").discoverProxySettings("http://www.example.com/");
		assertEquals(List.of("true"), result);
	}
}
