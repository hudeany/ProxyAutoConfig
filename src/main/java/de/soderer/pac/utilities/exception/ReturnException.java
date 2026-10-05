package de.soderer.pac.utilities.exception;

/**
 * Thrown by a "return" statement and caught by the enclosing function call, so a return value
 * is passed out of nested blocks and loops.
 */
public class ReturnException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** The return value. */
	private final transient Object returnValue;

	/**
	 * Creates a new exception without stack trace, as it is used for control flow only.
	 *
	 * @param returnValue
	 *            the return value, may be null
	 */
	public ReturnException(final Object returnValue) {
		super(null, null, false, false);
		this.returnValue = returnValue;
	}

	/**
	 * Returns the return value.
	 *
	 * @return the return value, may be null
	 */
	public Object getReturnValue() {
		return returnValue;
	}
}
