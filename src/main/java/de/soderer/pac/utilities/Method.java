package de.soderer.pac.utilities;

import java.util.List;

import de.soderer.pac.utilities.exception.ReturnException;

/**
 * A function defined by a PAC script.
 */
public class Method {
	private final String methodName;
	private final List<String> methodParameterNames;
	private final List<Statement> statements;

	/**
	 * Creates a function.
	 *
	 * @param methodName
	 *            the function name
	 * @param methodParameterNames
	 *            the parameter names
	 * @param methodBodyTokens
	 *            the tokens of the function body without the surrounding brackets
	 */
	public Method(final String methodName, final List<String> methodParameterNames, final List<String> methodBodyTokens) {
		this.methodName = methodName;
		this.methodParameterNames = methodParameterNames;
		statements = PacScriptParserUtilities.parseCodeBlockTokens(methodBodyTokens);
	}

	/**
	 * Returns the parameter names.
	 *
	 * @return the parameter names
	 */
	public List<String> getMethodParameterNames() {
		return methodParameterNames;
	}

	/**
	 * Executes the function in a sub context with the parameters as variables.
	 *
	 * @param context
	 *            the calling context
	 * @param methodParameters
	 *            the parameter values; missing values are undefined (null)
	 * @return the return value, or null
	 * @throws de.soderer.pac.utilities.exception.PacScriptExecutionLimitException
	 *             if the execution exceeds its limits
	 */
	public Object executeMethod(final Context context, final List<Object> methodParameters) {
		context.getExecutionGuard().enterMethodCall();
		try {
			final Context methodContext = context.createSubContext();
			for (int i = 0; i < methodParameterNames.size(); i++) {
				final String methodParameterName = methodParameterNames.get(i);
				// Missing parameters are undefined, like in JavaScript
				final Object methodParameter = i < methodParameters.size() ? methodParameters.get(i) : null;
				methodContext.setEnvironmentVariable(methodParameterName, methodParameter);
			}
			try {
				for (final Statement statement : statements) {
					statement.execute(methodContext);
				}
			} catch (final ReturnException e) {
				return e.getReturnValue();
			}
			// Function without return statement
			return null;
		} finally {
			context.getExecutionGuard().leaveMethodCall();
		}
	}

	@Override
	public String toString() {
		String returnValue = "";
		for (final String methodParameterName : methodParameterNames) {
			if (returnValue.length() > 0) {
				returnValue += ", ";
			}
			returnValue += methodParameterName;
		}
		returnValue = "function " + methodName + "(" + returnValue + ") {";
		for (final Statement statement : statements) {
			returnValue += "\n" + PacScriptParserUtilities.indentLines(statement.toString());
		}
		returnValue += "\n}";
		return returnValue;
	}
}
