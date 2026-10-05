package de.soderer.pac.utilities.exception;

/**
 * Thrown by a "break" statement and caught by the enclosing loop.
 */
public class BreakLoopException extends RuntimeException {
	/**
	 * Creates a new exception.
	 */
	public BreakLoopException() {
		super();
	}

	private static final long serialVersionUID = -2883441032675903474L;
}
