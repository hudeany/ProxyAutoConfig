package de.soderer.pac.utilities.exception;

/**
 * Thrown when a PAC script exceeds a configured execution limit
 * (step count, wall-clock time, or call/recursion depth), typically
 * indicating an infinite loop or runaway recursion in the script.
 */
public class PacScriptExecutionLimitException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public PacScriptExecutionLimitException(final String message) {
		super(message);
	}
}