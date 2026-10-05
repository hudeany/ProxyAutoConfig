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
		final Object conditionResult = condition.execute(context);
		if (conditionResult == null) {
			if (elseStatements != null) {
				for (final Statement elseStatement : elseStatements) {
					final Object result = elseStatement.execute(context);
					if (elseStatement instanceof Result) {
						return result;
					}
				}
				return null;
			} else {
				return null;
			}
		} else if (conditionResult instanceof Boolean) {
			if ((Boolean) conditionResult) {
				for (final Statement ifStatement : ifStatements) {
					final Object result = ifStatement.execute(context);
					if (result != null) {
						return result;
					}
				}
				return null;
			} else {
				if (elseStatements != null) {
					for (final Statement elseStatement : elseStatements) {
						final Object result = elseStatement.execute(context);
						if (result != null) {
							return result;
						}
					}
				}
				return null;
			}
		} else if (conditionResult instanceof Number) {
			if (((Number) conditionResult).doubleValue() > 0) {
				for (final Statement ifStatement : ifStatements) {
					final Object result = ifStatement.execute(context);
					if (ifStatement instanceof Result) {
						return result;
					}
				}
				return null;
			} else {
				if (elseStatements != null) {
					for (final Statement elseStatement : elseStatements) {
						final Object result = elseStatement.execute(context);
						if (elseStatement instanceof Result) {
							return result;
						}
					}
				}
				return null;
			}
		} else if (conditionResult instanceof String) {
			if (((String) conditionResult).trim().length() > 0) {
				for (final Statement ifStatement : ifStatements) {
					final Object result = ifStatement.execute(context);
					if (ifStatement instanceof Result) {
						return result;
					}
				}
				return null;
			} else {
				if (elseStatements != null) {
					for (final Statement elseStatement : elseStatements) {
						final Object result = elseStatement.execute(context);
						if (elseStatement instanceof Result) {
							return result;
						}
					}
				}
				return null;
			}
		} else {
			for (final Statement ifStatement : ifStatements) {
				final Object result = ifStatement.execute(context);
				if (ifStatement instanceof Result) {
					return result;
				}
			}
			return null;
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
