package de.soderer.pac.utilities;

import java.util.List;

/**
 * An if statement with optional else block.
 */
public class Condition implements Statement {
	private final Expression condition;
	private final List<Statement> ifStatements;
	private List<Statement> elseStatements;

	/**
	 * Creates an if statement.
	 *
	 * @param conditionTokens
	 *            the tokens of the condition
	 * @param ifCodeBlockTokens
	 *            the tokens of the if block
	 * @param elseCodeBlockTokens
	 *            the tokens of the else block, or null
	 */
	public Condition(final List<String> conditionTokens, final List<String> ifCodeBlockTokens, final List<String> elseCodeBlockTokens) {
		condition = new Expression(conditionTokens);
		ifStatements = PacScriptParserUtilities.parseCodeBlockTokens(ifCodeBlockTokens);
		if (elseCodeBlockTokens != null) {
			elseStatements = PacScriptParserUtilities.parseCodeBlockTokens(elseCodeBlockTokens);
		} else {
			elseStatements = null;
		}
	}

	/**
	 * Sets the else block by its tokens.
	 *
	 * @param elseCodeBlockTokens
	 *            the tokens of the else block, or null
	 */
	public void setElseCodeBlockTokens(final List<String> elseCodeBlockTokens) {
		if (elseCodeBlockTokens != null) {
			elseStatements = PacScriptParserUtilities.parseCodeBlockTokens(elseCodeBlockTokens);
		} else {
			elseStatements = null;
		}
	}

	/**
	 * Sets the else block by its tokens.
	 *
	 * @param newElseCodeBlockTokens
	 *            the tokens of the else block, or null
	 * @return this condition for chaining
	 */
	public Condition withElseCodeBlockTokens(final List<String> newElseCodeBlockTokens) {
		setElseCodeBlockTokens(newElseCodeBlockTokens);
		return this;
	}

	/**
	 * Sets the else block by its statements, e.g. for "else if".
	 *
	 * @param elseStatements
	 *            the statements of the else block
	 */
	public void setElseCodeBlockStatements(final List<Statement> elseStatements) {
		this.elseStatements = elseStatements;
	}

	/**
	 * Sets the else block by its statements, e.g. for "else if".
	 *
	 * @param newElseStatements
	 *            the statements of the else block
	 * @return this condition for chaining
	 */
	public Condition withElseCodeBlockStatements(final List<Statement> newElseStatements) {
		setElseCodeBlockStatements(newElseStatements);
		return this;
	}

	@Override
	public Object execute(final Context context) {
		final List<Statement> statementsToExecute = isTruthy(condition.execute(context)) ? ifStatements : elseStatements;
		if (statementsToExecute != null) {
			// A return statement leaves the block via ReturnException
			for (final Statement statement : statementsToExecute) {
				statement.execute(context);
			}
		}
		return null;
	}

	/**
	 * Converts a value to a boolean like JavaScript does: false, null (undefined), 0, NaN and the empty string are false, everything else is true.
	 *
	 * @param value
	 *            the value
	 * @return the boolean value
	 */
	public static boolean isTruthy(final Object value) {
		if (value == null) {
			return false;
		} else if (value instanceof Boolean) {
			return (Boolean) value;
		} else if (value instanceof Number) {
			final double doubleValue = ((Number) value).doubleValue();
			return doubleValue != 0 && !Double.isNaN(doubleValue);
		} else if (value instanceof String) {
			return !((String) value).isEmpty();
		} else {
			return true;
		}
	}

	@Override
	public String toString() {
		String returnValue = "if (" + condition.toString() + ") {\n";
		for (final Statement ifStatement : ifStatements) {
			returnValue += PacScriptParserUtilities.indentLines(ifStatement.toString()) + "\n";
		}
		if (elseStatements != null && !elseStatements.isEmpty()) {
			returnValue += "} else {\n";
			for (final Statement elseStatement : elseStatements) {
				returnValue += PacScriptParserUtilities.indentLines(elseStatement.toString()) + "\n";
			}
		}
		returnValue += "}";
		return returnValue;
	}
}
