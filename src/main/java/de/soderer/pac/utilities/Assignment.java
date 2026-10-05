package de.soderer.pac.utilities;

import java.util.List;

/**
 * A variable declaration or assignment, like "var x = 1" or "x = 2".
 */
public class Assignment implements Statement {
	/**
	 * Kinds of variable declarations.
	 */
	public enum Scope {
		/**
		 * Declaration by "var" or plain assignment.
		 */
		VAR,

		/**
		 * Declarations by 'let' keyword are block sensitive and may not be re-declarated
		 */
		LET,

		/**
		 * Declaration by "const", the variable cannot be changed.
		 */
		CONST
	}

	private final Scope scope;
	private final String variableName;
	private final Expression expression;

	private boolean alreadyExecutedOnce = false;

	/**
	 * Creates an assignment without declaration.
	 *
	 * @param variableName
	 *            the variable name
	 * @param expressionTokens
	 *            the tokens of the value expression
	 */
	public Assignment(final String variableName, final List<String> expressionTokens) {
		this(Scope.VAR, variableName, expressionTokens);
	}

	/**
	 * Creates a declaration.
	 *
	 * @param scope
	 *            the kind of declaration
	 * @param variableName
	 *            the variable name
	 * @param expressionTokens
	 *            the tokens of the value expression
	 */
	public Assignment(final Scope scope, final String variableName, final List<String> expressionTokens) {
		this.scope = scope;
		this.variableName = variableName;
		expression = new Expression(expressionTokens);
	}

	@Override
	public Object execute(final Context context) {
		if (scope == Scope.CONST && context.hasVariable(variableName) && !alreadyExecutedOnce) {
			throw new RuntimeException("Multiple declaration of variablename by 'const' keyword: " + variableName);
		} else if (context.isConstVariable(variableName) && !alreadyExecutedOnce) {
			throw new RuntimeException("Assignment to constant after declaration: " + variableName);
		} else if (scope == Scope.LET && context.hasVariable(variableName) && !alreadyExecutedOnce) {
			throw new RuntimeException("Multiple declaration of variablename by 'let' keyword: " + variableName);
		} else {
			// Expression must be executed always for concurrency results of unary operators
			final Object result = expression.execute(context);
			context.setEnvironmentVariable(variableName, result);
			if (scope == Scope.CONST) {
				context.setConstVariable(variableName);
			}
			alreadyExecutedOnce = true;
			return null;
		}
	}

	@Override
	public String toString() {
		if (scope == Scope.CONST) {
			return "const " + variableName + " = " + expression.toString() + ";";
		} else if (scope == Scope.LET) {
			return "let " + variableName + " = " + expression.toString() + ";";
		} else {
			return "var " + variableName + " = " + expression.toString() + ";";
		}
	}
}
