package de.soderer.pac.utilities;

import java.util.List;
import java.util.Map;

public class Condition implements Statement {
	private final Expression condition;
	private final List<Statement> ifStatements;
	private List<Statement> elseStatements;

	public Condition(final List<String> conditionTokens, final List<String> ifCodeBlockTokens, final List<String> elseCodeBlockTokens) {
		condition = new Expression(conditionTokens);
		ifStatements = PacScriptParserUtilities.parseCodeBlockTokens(ifCodeBlockTokens);
		if (elseCodeBlockTokens != null) {
			elseStatements = PacScriptParserUtilities.parseCodeBlockTokens(elseCodeBlockTokens);
		} else {
			elseStatements = null;
		}
	}

	@Override
	public Object execute(final Map<String, Object> environmentVariables, final Map<String, Method> definedMethods) {
		final Object conditionResult = condition.execute(environmentVariables, definedMethods);
		if (conditionResult == null) {
			if (elseStatements != null) {
				for (final Statement elseStatement : elseStatements) {
					final Object result = elseStatement.execute(environmentVariables, definedMethods);
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
					final Object result = ifStatement.execute(environmentVariables, definedMethods);
					if (result != null) {
						return result;
					}
				}
				return null;
			} else {
				if (elseStatements != null) {
					for (final Statement elseStatement : elseStatements) {
						final Object result = elseStatement.execute(environmentVariables, definedMethods);
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
					final Object result = ifStatement.execute(environmentVariables, definedMethods);
					if (ifStatement instanceof Result) {
						return result;
					}
				}
				return null;
			} else {
				if (elseStatements != null) {
					for (final Statement elseStatement : elseStatements) {
						final Object result = elseStatement.execute(environmentVariables, definedMethods);
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
					final Object result = ifStatement.execute(environmentVariables, definedMethods);
					if (ifStatement instanceof Result) {
						return result;
					}
				}
				return null;
			} else {
				if (elseStatements != null) {
					for (final Statement elseStatement : elseStatements) {
						final Object result = elseStatement.execute(environmentVariables, definedMethods);
						if (elseStatement instanceof Result) {
							return result;
						}
					}
				}
				return null;
			}
		} else {
			for (final Statement ifStatement : ifStatements) {
				final Object result = ifStatement.execute(environmentVariables, definedMethods);
				if (ifStatement instanceof Result) {
					return result;
				}
			}
			return null;
		}
	}
}
