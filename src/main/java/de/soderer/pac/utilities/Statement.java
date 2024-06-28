package de.soderer.pac.utilities;

import java.util.Map;

public interface Statement {
	Object execute(Map<String, Object> environmentVariables, Map<String, Method> definedMethods);
}
