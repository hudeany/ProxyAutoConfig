package de.soderer.pac.utilities.exception;

/**
 * Thrown by a "continue" statement and caught by the enclosing loop.
 */
public class ContinueLoopException extends RuntimeException {
	/**
	 * Creates a new exception.
	 */
	public ContinueLoopException() {
		super();
	}

	private static final long serialVersionUID = 1437437697712695505L;
}
