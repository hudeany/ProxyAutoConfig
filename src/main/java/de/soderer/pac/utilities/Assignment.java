package de.soderer.pac.utilities;

import java.util.List;
import java.util.Map;

public class Assignment implements Statement {
	/**
	 * Declarations by 'let' keyword are block sensitive and may not be re-declarated
	 */
	private final boolean isLetDeclaration;
	private final String variableName;
	private final Expression expression;

	public Assignment(final boolean isLetDeclaration, final String variableName, final List<String> expressionTokens) {
		this.isLetDeclaration = isLetDeclaration;
		this.variableName = variableName;
		expression = new Expression(expressionTokens);
	}

	@Override
	public Object execute(final Map<String, Object> environmentVariables, final Map<String, Method> definedMethods) {
		if (isLetDeclaration && environmentVariables.containsKey(variableName)) {
			throw new RuntimeException("Multiple declaration of variablename by 'let' keyword");
		} else {
			environmentVariables.put(variableName, expression.execute(environmentVariables, definedMethods));
			return null;
		}
	}

	@Override
	public String toString() {
		if (isLetDeclaration) {
			return "let " + variableName + " = " + expression.toString() + ";\n";
		} else {
			return "var " + variableName + " = " + expression.toString() + ";\n";
		}
	}
}
