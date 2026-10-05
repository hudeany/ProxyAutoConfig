package de.soderer.pac.utilities;

import java.util.List;

import de.soderer.pac.utilities.exception.ReturnException;

/**
 * A return statement.
 */
public class Result implements Statement {
	private final Expression expression;

	/**
	 * Creates a return statement.
	 *
	 * @param expressionTokens
	 *            the tokens of the return value expression
	 */
	public Result(final List<String> expressionTokens) {
		// "return;" without value
		expression = expressionTokens == null || expressionTokens.isEmpty() ? null : new Expression(expressionTokens);
	}

	@Override
	public Object execute(final Context context) {
		// Leaves all enclosing blocks and loops up to the function call
		throw new ReturnException(expression == null ? null : expression.execute(context));
	}

	@Override
	public String toString() {
		return expression == null ? "return;" : "return " + expression.toString() + ";";
	}
}
