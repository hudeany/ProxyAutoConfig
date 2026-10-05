package de.soderer.pac.utilities;

/**
 * A statement of a PAC script that can be executed.
 */
public interface Statement {
	/**
	 * Executes the statement.
	 *
	 * @param context
	 *            the variables and functions of the current scope
	 * @return the result: a return value, the value of an expression, or null
	 */
	Object execute(Context context);
}
