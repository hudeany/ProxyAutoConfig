package de.soderer.pac.utilities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Context {
	private Map<String, Method> definedMethods = new HashMap<>();
	private Map<String, Object> environmentVariables = new HashMap<>();
	private Set<String> constVariables = new HashSet<>();

	private final ExecutionGuard executionGuard;

	public Context() {
		this(new ExecutionGuard());
	}

	public Context(final ExecutionGuard executionGuard) {
		this.executionGuard = executionGuard;
	}

	public ExecutionGuard getExecutionGuard() {
		return executionGuard;
	}

	public Method getDefinedMethod(final String methodName) {
		return definedMethods.get(methodName);
	}

	public void setDefinedMethod(final String methodName, final Method method) {
		definedMethods.put(methodName, method);
	}

	public Context withDefinedMethod(final String newMethodName, final Method newMethod) {
		setDefinedMethod(newMethodName, newMethod);
		return this;
	}

	public boolean hasDefinedMethod(final String methodName) {
		return definedMethods.containsKey(methodName);
	}

	public Object getEnvironmentVariable(final String variableName) {
		return environmentVariables.get(variableName);
	}

	public void setEnvironmentVariable(final String variableName, final Object variableValue) {
		environmentVariables.put(variableName, variableValue);
	}

	public Context withEnvironmentVariable(final String newVariableName, final Object newVariableValue) {
		setEnvironmentVariable(newVariableName, newVariableValue);
		return this;
	}

	public boolean hasVariable(final String variableName) {
		return environmentVariables.containsKey(variableName);
	}

	public Boolean isConstVariable(final String variableName) {
		return constVariables.contains(variableName);
	}

	public void setConstVariable(final String variableName) {
		constVariables.add(variableName);
	}

	public Context withConstVariable(final String newVariableName) {
		setConstVariable(newVariableName);
		return this;
	}

	public Context createSubContext() {
		// Shared reference of executionGuard by intention
		final Context subContext = new Context(executionGuard);

		subContext.definedMethods = new HashMap<>(definedMethods);
		subContext.environmentVariables = new HashMap<>(environmentVariables);
		subContext.constVariables = new HashSet<>(constVariables);
		return subContext;
	}
}
