package de.soderer.pac.utilities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Execution scope of a PAC script: defined functions, variables and constants. Blocks and
 * function calls use a sub context, so their variables do not change the outer scope, while all
 * contexts of one execution share one {@link ExecutionGuard}.
 */
public class Context {
	/**
	 * Functions defined by the script.
	 */
	private Map<String, Method> definedMethods = new HashMap<>();
	/**
	 * Variables by name.
	 */
	private Map<String, Object> environmentVariables = new HashMap<>();
	/**
	 * Names of constants.
	 */
	private Set<String> constVariables = new HashSet<>();

	/**
	 * Limits of the execution.
	 */
	private final ExecutionGuard executionGuard;

	/**
	 * Creates a context with default execution limits.
	 */
	public Context() {
		this(new ExecutionGuard());
	}

	/**
	 * Creates a context.
	 *
	 * @param executionGuard
	 *            the limits of the execution
	 */
	public Context(final ExecutionGuard executionGuard) {
		this.executionGuard = executionGuard;
	}

	/**
	 * Returns the limits of the execution.
	 *
	 * @return the execution guard
	 */
	public ExecutionGuard getExecutionGuard() {
		return executionGuard;
	}

	/**
	 * Returns a function defined by the script.
	 *
	 * @param methodName
	 *            the function name
	 * @return the function, or null
	 */
	public Method getDefinedMethod(final String methodName) {
		return definedMethods.get(methodName);
	}

	/**
	 * Defines a function.
	 *
	 * @param methodName
	 *            the function name
	 * @param method
	 *            the function
	 */
	public void setDefinedMethod(final String methodName, final Method method) {
		definedMethods.put(methodName, method);
	}

	/**
	 * Defines a function.
	 *
	 * @param newMethodName
	 *            the function name
	 * @param newMethod
	 *            the function
	 * @return this context for chaining
	 */
	public Context withDefinedMethod(final String newMethodName, final Method newMethod) {
		setDefinedMethod(newMethodName, newMethod);
		return this;
	}

	/**
	 * Checks whether a function is defined by the script.
	 *
	 * @param methodName
	 *            the function name
	 * @return true, if the function is defined
	 */
	public boolean hasDefinedMethod(final String methodName) {
		return definedMethods.containsKey(methodName);
	}

	/**
	 * Returns the value of a variable.
	 *
	 * @param variableName
	 *            the variable name
	 * @return the value, or null
	 */
	public Object getEnvironmentVariable(final String variableName) {
		return environmentVariables.get(variableName);
	}

	/**
	 * Sets the value of a variable.
	 *
	 * @param variableName
	 *            the variable name
	 * @param variableValue
	 *            the value
	 */
	public void setEnvironmentVariable(final String variableName, final Object variableValue) {
		environmentVariables.put(variableName, variableValue);
	}

	/**
	 * Sets the value of a variable.
	 *
	 * @param newVariableName
	 *            the variable name
	 * @param newVariableValue
	 *            the value
	 * @return this context for chaining
	 */
	public Context withEnvironmentVariable(final String newVariableName, final Object newVariableValue) {
		setEnvironmentVariable(newVariableName, newVariableValue);
		return this;
	}

	/**
	 * Checks whether a variable exists.
	 *
	 * @param variableName
	 *            the variable name
	 * @return true, if the variable exists
	 */
	public boolean hasVariable(final String variableName) {
		return environmentVariables.containsKey(variableName);
	}

	/**
	 * Checks whether a variable is a constant.
	 *
	 * @param variableName
	 *            the variable name
	 * @return true, if the variable was declared by "const"
	 */
	public Boolean isConstVariable(final String variableName) {
		return constVariables.contains(variableName);
	}

	/**
	 * Marks a variable as constant.
	 *
	 * @param variableName
	 *            the variable name
	 */
	public void setConstVariable(final String variableName) {
		constVariables.add(variableName);
	}

	/**
	 * Marks a variable as constant.
	 *
	 * @param newVariableName
	 *            the variable name
	 * @return this context for chaining
	 */
	public Context withConstVariable(final String newVariableName) {
		setConstVariable(newVariableName);
		return this;
	}

	/**
	 * Creates a sub context for a block or function call with copies of the functions, variables
	 * and constants, and the same execution guard.
	 *
	 * @return the sub context
	 */
	public Context createSubContext() {
		// Shared reference of executionGuard by intention
		final Context subContext = new Context(executionGuard);

		subContext.definedMethods = new HashMap<>(definedMethods);
		subContext.environmentVariables = new HashMap<>(environmentVariables);
		subContext.constVariables = new HashSet<>(constVariables);
		return subContext;
	}
}
