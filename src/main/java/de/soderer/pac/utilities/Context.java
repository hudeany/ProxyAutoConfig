package de.soderer.pac.utilities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Context {
	Map<String, Method> definedMethods = new HashMap<>();
	Map<String, Object> environmentVariables = new HashMap<>();
	Set<String> constVariables = new HashSet<>();

	public Method getDefinedMethod(final String methodName) {
		return definedMethods.get(methodName);
	}

	public void setDefinedMethod(final String methodName, final Method method) {
		definedMethods.put(methodName, method);
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

	public boolean hasVariable(final String variableName) {
		return environmentVariables.containsKey(variableName);
	}

	public Boolean isConstVariable(final String variableName) {
		return constVariables.contains(variableName);
	}

	public void setConstVariable(final String variableName) {
		constVariables.add(variableName);
	}

	public Context createSubContext() {
		final Context subContext = new Context();
		subContext.definedMethods = new HashMap<>(definedMethods);
		subContext.environmentVariables = new HashMap<>(environmentVariables);
		subContext.constVariables = new HashSet<>(constVariables);
		return subContext;
	}
}
