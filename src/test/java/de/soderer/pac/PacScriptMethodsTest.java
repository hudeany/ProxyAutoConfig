package de.soderer.pac;

import org.junit.Assert;
import org.junit.Test;

import de.soderer.pac.utilities.PacScriptMethods;

@SuppressWarnings("static-method")
public class PacScriptMethodsTest {
	@Test
	public void isInNetTest() {
		try {
			Assert.assertTrue(PacScriptMethods.isInNet("198.95.249.79", "198.95.249.79", "255.255.255.255"));
			Assert.assertTrue(PacScriptMethods.isInNet("198.95.123.123", "198.95.0.0", "255.255.0.0"));

			Assert.assertFalse(PacScriptMethods.isInNet("198.96.249.79", "198.95.249.79", "255.255.255.255"));
			Assert.assertFalse(PacScriptMethods.isInNet("198.96.123.123", "198.95.0.0", "255.255.255.255"));
			Assert.assertFalse(PacScriptMethods.isInNet("198.95.123.123", "198.95.0.0", "255.255.255.255"));
			Assert.assertFalse(PacScriptMethods.isInNet("198.95.249.79", "198.95.x49.79", "255.255.255.255"));
			Assert.assertFalse(PacScriptMethods.isInNet("198.95.x49.79", "198.95.249.79", "255.255.255.255"));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void isInNetExTest() {
		try {
			Assert.assertTrue(PacScriptMethods.isInNetEx("198.95.249.79", "198.95.249.79/32"));
			Assert.assertTrue(PacScriptMethods.isInNetEx("198.95.123.123", "198.95.0.0/16"));
			Assert.assertTrue(PacScriptMethods.isInNetEx("3ffe:8311:ffff:123:123:123:123:123", "3ffe:8311:ffff::/48"));
			Assert.assertTrue(PacScriptMethods.isInNetEx("3ffe:8311:ffff::123", "3ffe:8311:ffff::/48"));
			Assert.assertTrue(PacScriptMethods.isInNetEx("3ffe:8311:ffff::", "3ffe:8311:ffff::/48"));

			Assert.assertFalse(PacScriptMethods.isInNetEx("198.96.249.79", "198.95.249.79/32"));
			Assert.assertFalse(PacScriptMethods.isInNetEx("198.96.123.123", "198.95.0.0/16"));
			Assert.assertFalse(PacScriptMethods.isInNetEx("198.95.123.123", "198.95.0.0/1c"));
			Assert.assertFalse(PacScriptMethods.isInNetEx("198.95.249.79", "198.95.x49.79/32"));
			Assert.assertFalse(PacScriptMethods.isInNetEx("198.95.x49.79", "198.95.249.79/32"));
			Assert.assertFalse(PacScriptMethods.isInNetEx("3ffe:8311:ffff:123:123:123:123:123", "3ffe:8311:fxff::/48"));
		} catch (final Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
}
