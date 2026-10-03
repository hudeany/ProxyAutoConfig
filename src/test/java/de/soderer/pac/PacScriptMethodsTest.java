package de.soderer.pac;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.soderer.pac.utilities.PacScriptMethods;

@SuppressWarnings("static-method")
public class PacScriptMethodsTest {
	@Test
	public void isInNetTest() {
		try {
			Assertions.assertTrue(PacScriptMethods.isInNet("198.95.249.79", "198.95.249.79", "255.255.255.255"));
			Assertions.assertTrue(PacScriptMethods.isInNet("198.95.123.123", "198.95.0.0", "255.255.0.0"));

			Assertions.assertFalse(PacScriptMethods.isInNet("198.96.249.79", "198.95.249.79", "255.255.255.255"));
			Assertions.assertFalse(PacScriptMethods.isInNet("198.96.123.123", "198.95.0.0", "255.255.255.255"));
			Assertions.assertFalse(PacScriptMethods.isInNet("198.95.123.123", "198.95.0.0", "255.255.255.255"));
			Assertions.assertFalse(PacScriptMethods.isInNet("198.95.249.79", "198.95.x49.79", "255.255.255.255"));
			Assertions.assertFalse(PacScriptMethods.isInNet("198.95.x49.79", "198.95.249.79", "255.255.255.255"));
		} catch (final Exception e) {
			e.printStackTrace();
			Assertions.fail(e.getMessage());
		}
	}

	@Test
	public void isInNetExTest() {
		try {
			Assertions.assertTrue(PacScriptMethods.isInNetEx("198.95.249.79", "198.95.249.79/32"));
			Assertions.assertTrue(PacScriptMethods.isInNetEx("198.95.123.123", "198.95.0.0/16"));
			Assertions.assertTrue(PacScriptMethods.isInNetEx("3ffe:8311:ffff:123:123:123:123:123", "3ffe:8311:ffff::/48"));
			Assertions.assertTrue(PacScriptMethods.isInNetEx("3ffe:8311:ffff::123", "3ffe:8311:ffff::/48"));
			Assertions.assertTrue(PacScriptMethods.isInNetEx("3ffe:8311:ffff::", "3ffe:8311:ffff::/48"));

			Assertions.assertFalse(PacScriptMethods.isInNetEx("198.96.249.79", "198.95.249.79/32"));
			Assertions.assertFalse(PacScriptMethods.isInNetEx("198.96.123.123", "198.95.0.0/16"));
			Assertions.assertFalse(PacScriptMethods.isInNetEx("198.95.123.123", "198.95.0.0/1c"));
			Assertions.assertFalse(PacScriptMethods.isInNetEx("198.95.249.79", "198.95.x49.79/32"));
			Assertions.assertFalse(PacScriptMethods.isInNetEx("198.95.x49.79", "198.95.249.79/32"));
			Assertions.assertFalse(PacScriptMethods.isInNetEx("3ffe:8311:ffff:123:123:123:123:123", "3ffe:8311:fxff::/48"));
		} catch (final Exception e) {
			e.printStackTrace();
			Assertions.fail(e.getMessage());
		}
	}
}
