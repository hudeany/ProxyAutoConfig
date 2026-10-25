package de.soderer.pac.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import de.soderer.pac.utilities.exception.BreakLoopException;
import de.soderer.pac.utilities.exception.ContinueLoopException;

/**
 * An expression of a PAC script: a literal, variable, array, function call, or an operation
 * with JavaScript operator precedence.
 */
public class Expression implements Statement {
	private final static List<List<String>> PRECEDENCE_LEVELS = Arrays.asList(
			Arrays.asList("||"),
			Arrays.asList("&&"),
			Arrays.asList("|"),
			Arrays.asList("&"),
			Arrays.asList("==", "!="),
			Arrays.asList("<", ">", "<=", ">="),
			Arrays.asList("+", "-"),
			Arrays.asList("*", "/", "%")
	);

	private final static List<String> unaryOperators = Arrays.asList(new String[] {
			"++", "--", "!"
	});

	private final static List<String> prefixOperators = Arrays.asList(new String[] {
			"++", "--", "!", "-", "+"
	});

	/** All operator tokens, used to detect whether a "+" or "-" is a unary sign */
	private final static List<String> allOperators = Arrays.asList(new String[] {
			"||", "&&", "|", "&", "==", "!=", "<", ">", "<=", ">=", "+", "-", "*", "/", "%", "!", "=", "+=", "-=", "*=", "/="
	});

	/** Strict JavaScript decimal number literal (without sign) */
	private final static Pattern DECIMAL_NUMBER_PATTERN = Pattern.compile("^([0-9]+\\.?[0-9]*|\\.[0-9]+)([eE][+-]?[0-9]+)?$");

	/** JavaScript hexadecimal number literal */
	private final static Pattern HEX_NUMBER_PATTERN = Pattern.compile("^0[xX][0-9a-fA-F]+$");

	private List<String> expressionTokens;

	private Expression expression1;
	private String operator;
	private Expression expression2;

	/** Expression was enclosed in round brackets, which are kept for toString() */
	private boolean bracketed = false;

	/**
	 * Returns the tokens of the expression.
	 *
	 * @return the tokens
	 */
	public List<String> getExpressionTokens() {
		return expressionTokens;
	}

	/**
	 * Creates an expression.
	 *
	 * @param expressionTokens
	 *            the tokens of the expression
	 */
	public Expression(final List<String> expressionTokens) {
		if (expressionTokens == null || expressionTokens.size() == 0) {
			throw new RuntimeException("Unsupported empty expression");
		} else if ("(".equals(expressionTokens.get(0)) && PacScriptParserUtilities.findClosingBracketToken(expressionTokens, 0) == expressionTokens.size() - 1) {
			// Whole expression is enclosed in brackets
			expression1 = new Expression(expressionTokens.subList(1, expressionTokens.size() - 1));
			bracketed = true;
		} else if ("[".equals(expressionTokens.get(0)) && PacScriptParserUtilities.findClosingBracketToken(expressionTokens, 0) == expressionTokens.size() - 1) {
			// Array literal
			this.expressionTokens = expressionTokens;
		} else {
			int operatorIndex = -1;
			for (final List<String> precedenceLevel : PRECEDENCE_LEVELS) {
				operatorIndex = lastIndexOfBinaryOperatorOutsideOfBrackets(expressionTokens, precedenceLevel);
				if (operatorIndex > -1) {
					break;
				}
			}

			if (operatorIndex > -1) {
				expression1 = new Expression(expressionTokens.subList(0, operatorIndex));
				operator = expressionTokens.get(operatorIndex);
				expression2 = new Expression(expressionTokens.subList(operatorIndex + 1, expressionTokens.size()));
			} else if ("[".equals(expressionTokens.get(0))) {
				final int bracketEnd = PacScriptParserUtilities.findClosingBracketToken(expressionTokens, 0);
				throw new RuntimeException("Unsupported expression with operator for array found: " + expressionTokens.get(bracketEnd + 1));
			} else if (prefixOperators.contains(expressionTokens.get(0))) {
				if (expressionTokens.size() == 1) {
					throw new RuntimeException("Missing operand for unary operator: " + expressionTokens.get(0));
				}
				operator = expressionTokens.get(0);
				expression2 = new Expression(expressionTokens.subList(1, expressionTokens.size()));
			} else {
				final int unaryOperatorIndex = PacScriptParserUtilities.indexOfOperatorOutsideOfBrackets(expressionTokens, unaryOperators);
				if (unaryOperatorIndex == -1) {
					this.expressionTokens = expressionTokens;
				} else if (unaryOperatorIndex == expressionTokens.size() - 1 && ("++".equals(expressionTokens.get(unaryOperatorIndex)) || "--".equals(expressionTokens.get(unaryOperatorIndex)))) {
					expression1 = new Expression(expressionTokens.subList(0, unaryOperatorIndex));
					operator = expressionTokens.get(unaryOperatorIndex);
				} else {
					throw new RuntimeException("Unsupported expression with unary operator found: " + expressionTokens.get(unaryOperatorIndex));
				}
			}
		}
	}

	/**
	 * Finds the last binary operator of the given precedence level outside of brackets.
	 * A "+" or "-" directly following another operator, an opening bracket or a comma, or standing at the start, is a unary sign and is skipped.
	 */
	private static int lastIndexOfBinaryOperatorOutsideOfBrackets(final List<String> tokens, final List<String> operators) {
		int openBrackets = 0;
		int lastMatch = -1;
		for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
			final String token = tokens.get(tokenIndex);
			if ("(".equals(token) || "[".equals(token)) {
				openBrackets++;
			} else if (")".equals(token) || "]".equals(token)) {
				openBrackets--;
			} else if (openBrackets == 0 && operators.contains(token)) {
				if (("+".equals(token) || "-".equals(token)) && !isPrecededByValue(tokens, tokenIndex)) {
					continue;
				}
				lastMatch = tokenIndex;
			}
		}
		return lastMatch;
	}

	private static boolean isPrecededByValue(final List<String> tokens, final int tokenIndex) {
		if (tokenIndex == 0) {
			return false;
		}
		final String previousToken = tokens.get(tokenIndex - 1);
		if ("++".equals(previousToken) || "--".equals(previousToken)) {
			// Postfix increment/decrement only, if there is an operand before it
			return isPrecededByValue(tokens, tokenIndex - 1);
		}
		return !allOperators.contains(previousToken) && !"(".equals(previousToken) && !"[".equals(previousToken) && !",".equals(previousToken);
	}

	@Override
	public Object execute(final Context context) {
		if (expressionTokens == null) {
			if (expression1 == null) {
				// Prefix operator
				final Object result2 = expression2.execute(context);
				if ("++".equals(operator) && result2 instanceof Integer) {
					final int value = ((Integer) result2) + 1;
					if (expression2.getExpressionTokens() != null && expression2.getExpressionTokens().size() == 1 && context.hasVariable(expression2.getExpressionTokens().get(0))) {
						context.setEnvironmentVariable(expression2.getExpressionTokens().get(0), value);
					}
					return value;
				} else if ("--".equals(operator) && result2 instanceof Integer) {
					final int value = ((Integer) result2) - 1;
					if (expression2.getExpressionTokens() != null && expression2.getExpressionTokens().size() == 1 && context.hasVariable(expression2.getExpressionTokens().get(0))) {
						context.setEnvironmentVariable(expression2.getExpressionTokens().get(0), value);
					}
					return value;
				} else if ("!".equals(operator) && result2 instanceof Boolean) {
					return !(Boolean) result2;
				} else if ("-".equals(operator) && result2 instanceof Integer) {
					return -((Integer) result2);
				} else if ("-".equals(operator) && result2 instanceof Number) {
					return -((Number) result2).doubleValue();
				} else if ("+".equals(operator) && result2 instanceof Number) {
					return result2;
				} else {
					throw new RuntimeException("Unsupported operator: " + operator);
				}
			} else if (operator == null) {
				// Bracketed expression
				return expression1.execute(context);
			}

			final Object result1 = expression1.execute(context);
			if (expression2 == null) {
				// Postfix operator
				if ("++".equals(operator) && result1 instanceof Integer) {
					final int value = ((Integer) result1) + 1;
					if (expression1.getExpressionTokens() != null && expression1.getExpressionTokens().size() == 1 && context.hasVariable(expression1.getExpressionTokens().get(0))) {
						context.setEnvironmentVariable(expression1.getExpressionTokens().get(0), value);
					}
					return result1;
				} else if ("--".equals(operator) && result1 instanceof Integer) {
					final int value = ((Integer) result1) - 1;
					if (expression1.getExpressionTokens() != null && expression1.getExpressionTokens().size() == 1 && context.hasVariable(expression1.getExpressionTokens().get(0))) {
						context.setEnvironmentVariable(expression1.getExpressionTokens().get(0), value);
					}
					return result1;
				} else {
					throw new RuntimeException("Unsupported operator: " + operator);
				}
			}

			// Short-circuit evaluation of logical operators, like in JavaScript
			if ("&&".equals(operator) && Boolean.FALSE.equals(result1)) {
				return false;
			} else if ("||".equals(operator) && Boolean.TRUE.equals(result1)) {
				return true;
			}

			final Object result2 = expression2.execute(context);
			if (result1 == null || result2 == null) {
				if ("==".equals(operator)) {
					return result1 == result2;
				} else if ("!=".equals(operator)) {
					return result1 != result2;
				} else {
					throw new RuntimeException("Unsupported null parameter for operator: " + operator);
				}
			} else {
				if ("==".equals(operator)) {
					if (result1 == result2) {
						return true;
					} else if (result1 instanceof String && result2 instanceof String) {
						return ((String) result1).equals(result2);
					} else if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) == (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() == ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("!=".equals(operator)) {
					if (result1 == result2) {
						return false;
					} else if (result1 instanceof String && result2 instanceof String) {
						return !((String) result1).equals(result2);
					} else if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) != (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() != ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if (">".equals(operator)) {
					if (result1 == result2) {
						return false;
					} else if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) > (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() > ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("<".equals(operator)) {
					if (result1 == result2) {
						return false;
					} else if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) < (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() < ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if (">=".equals(operator)) {
					if (result1 == result2) {
						return true;
					} else if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) >= (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() >= ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("<=".equals(operator)) {
					if (result1 == result2) {
						return true;
					} else if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) <= (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() <= ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("+".equals(operator)) {
					if (result1 instanceof String && result2 instanceof String) {
						return ((String) result1) + (String) result2;
					} else if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) + (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() + ((Number) result2).doubleValue();
					} else if (result1 instanceof String) {
						return result1 + result2.toString();
					} else if (result2 instanceof String) {
						return result1.toString() + result2;
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("-".equals(operator)) {
					if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) - (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() - ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("*".equals(operator)) {
					if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) * (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() * ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("/".equals(operator)) {
					if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) / (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() / ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("%".equals(operator)) {
					if (result1 instanceof Integer && result2 instanceof Integer) {
						return ((Integer) result1) % (Integer) result2;
					} else if (bothNumbers(result1, result2)) {
						return ((Number) result1).doubleValue() % ((Number) result2).doubleValue();
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("&".equals(operator)) {
					if (result1 instanceof Boolean && result2 instanceof Boolean) {
						return ((Boolean) result1) & (Boolean) result2;
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("&&".equals(operator)) {
					if (result1 instanceof Boolean && result2 instanceof Boolean) {
						return ((Boolean) result1) && (Boolean) result2;
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("|".equals(operator)) {
					if (result1 instanceof Boolean && result2 instanceof Boolean) {
						return ((Boolean) result1) | (Boolean) result2;
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else if ("||".equals(operator)) {
					if (result1 instanceof Boolean && result2 instanceof Boolean) {
						return ((Boolean) result1) || (Boolean) result2;
					} else {
						throw new RuntimeException("Unsupported parameters for operator: " + operator);
					}
				} else {
					throw new RuntimeException("Unsupported operator: " + operator);
				}
			}
		} else {
			final String firstToken = expressionTokens.get(0);
			if ("break".equals(firstToken)) {
				throw new BreakLoopException();
			} else if ("continue".equals(firstToken)) {
				throw new ContinueLoopException();
			} else if (expressionTokens.size() > 1 && firstToken.equals("[") && expressionTokens.get(expressionTokens.size() - 1).equals("]")) {
				final List<Object> array = new ArrayList<>();
				final List<List<String>> arrayItemTokens = readArrayItems(expressionTokens.subList(1, expressionTokens.size() - 1));
				for (final List<String> itemTokens : arrayItemTokens) {
					final Object nextItem = new Expression(itemTokens).execute(context);
					array.add(nextItem);
				}
				return array;
			} else if (expressionTokens.size() > 1 && expressionTokens.get(1).equals("(")) {
				if (PacScriptParserUtilities.findClosingBracketToken(expressionTokens, 1) != expressionTokens.size() - 1) {
					throw new RuntimeException("Unexpected expression when expecting single value");
				}
				final List<Object> methodCallParameters = readMethodParameters(context, 1);

				if (context.hasDefinedMethod(firstToken)) {
					return context.getDefinedMethod(firstToken).executeMethod(context, methodCallParameters);
				} else if ("isPlainHostName".equals(firstToken)) {
					return PacScriptMethods.isPlainHostName((String) methodCallParameters.get(0));
				} else if ("dnsDomainIs".equals(firstToken)) {
					return PacScriptMethods.dnsDomainIs((String) methodCallParameters.get(0), (String) methodCallParameters.get(1));
				} else if ("localHostOrDomainIs".equals(firstToken)) {
					return PacScriptMethods.localHostOrDomainIs((String) methodCallParameters.get(0), (String) methodCallParameters.get(1));
				} else if ("isResolvable".equals(firstToken)) {
					return PacScriptMethods.isResolvable((String) methodCallParameters.get(0));
				} else if ("isInNet".equals(firstToken)) {
					return PacScriptMethods.isInNet((String) methodCallParameters.get(0), (String) methodCallParameters.get(1), (String) methodCallParameters.get(2));
				} else if ("dnsResolve".equals(firstToken)) {
					return PacScriptMethods.dnsResolve((String) methodCallParameters.get(0));
				} else if ("myIpAddress".equals(firstToken)) {
					return PacScriptMethods.myIpAddress();
				} else if ("dnsDomainLevels".equals(firstToken)) {
					return PacScriptMethods.dnsDomainLevels((String) methodCallParameters.get(0));
				} else if ("shExpMatch".equals(firstToken)) {
					return PacScriptMethods.shExpMatch((String) methodCallParameters.get(0), (String) methodCallParameters.get(1));
				} else if ("weekdayRange".equals(firstToken)) {
					return PacScriptMethods.weekdayRange((String) getParameter(methodCallParameters, 0), (String) getParameter(methodCallParameters, 1), (String) getParameter(methodCallParameters, 2));
				} else if ("dateRange".equals(firstToken)) {
					return PacScriptMethods.dateRange(getParameter(methodCallParameters, 0), getParameter(methodCallParameters, 1), getParameter(methodCallParameters, 2), getParameter(methodCallParameters, 3), getParameter(methodCallParameters, 4), getParameter(methodCallParameters, 5), getParameter(methodCallParameters, 6));
				} else if ("timeRange".equals(firstToken)) {
					return PacScriptMethods.timeRange(getParameter(methodCallParameters, 0), getParameter(methodCallParameters, 1), getParameter(methodCallParameters, 2), getParameter(methodCallParameters, 3), getParameter(methodCallParameters, 4), getParameter(methodCallParameters, 5), getParameter(methodCallParameters, 6));
				} else if ("isResolvableEx".equals(firstToken)) {
					return PacScriptMethods.isResolvableEx((String) methodCallParameters.get(0));
				} else if ("isInNetEx".equals(firstToken)) {
					return PacScriptMethods.isInNetEx((String) methodCallParameters.get(0), (String) methodCallParameters.get(1));
				} else if ("dnsResolveEx".equals(firstToken)) {
					return PacScriptMethods.dnsResolveEx((String) methodCallParameters.get(0));
				} else if ("myIpAddressEx".equals(firstToken)) {
					return PacScriptMethods.myIpAddressEx();
				} else if ("sortIpAddressList".equals(firstToken)) {
					return PacScriptMethods.sortIpAddressList((String) methodCallParameters.get(0));
				} else if ("getClientVersion".equals(firstToken)) {
					return PacScriptMethods.getClientVersion();
				} else if ("alert".equals(firstToken)) {
					// Debug output of browsers, ignored here
					return null;
				} else {
					throw new RuntimeException("Call of undefined method: " + firstToken);
				}
			} else if (expressionTokens.size() > 1 && expressionTokens.get(1).equals("[")) {
				if (PacScriptParserUtilities.findClosingBracketToken(expressionTokens, 1) != expressionTokens.size() - 1) {
					throw new RuntimeException("Unexpected expression when expecting single value");
				} else if (context.hasVariable(firstToken)) {
					final int arrayIndex = readArrayIndex(context, expressionTokens, 1);
					final Object arrayObject = context.getEnvironmentVariable(firstToken);
					if (arrayObject == null || !(arrayObject instanceof List)) {
						throw new RuntimeException("Invalid array reference: " + firstToken);
					} else {
						@SuppressWarnings("unchecked")
						final List<Object> array = (List<Object>) arrayObject;
						if (array.size() <= arrayIndex) {
							throw new RuntimeException("Invalid array index: " + arrayIndex);
						} else {
							return array.get(arrayIndex);
						}
					}
				} else {
					throw new RuntimeException("Array Index call for undefined variable: " + firstToken);
				}
			} else {
				if (expressionTokens.size() > 1) {
					throw new RuntimeException("Unexpected expression when expecting single value");
				} else if ("true".equals(firstToken)) {
					return true;
				} else if ("false".equals(firstToken)) {
					return false;
				} else if (context.hasVariable(firstToken)) {
					return context.getEnvironmentVariable(firstToken);
				} else if ("null".equals(firstToken) || "undefined".equals(firstToken)) {
					return null;
				} else if (firstToken.length() >= 2 && ((firstToken.startsWith("\"") && firstToken.endsWith("\"")) || (firstToken.startsWith("'") && firstToken.endsWith("'")))) {
					return unescapeStringLiteral(firstToken.substring(1, firstToken.length() - 1));
				} else if (firstToken.endsWith(".length")) {
					final String arrayName = firstToken.substring(0, firstToken.length() - 7);
					if (context.hasVariable(arrayName)) {
						final Object arrayObject = context.getEnvironmentVariable(arrayName);
						if (arrayObject != null && arrayObject instanceof List<?>) {
							return ((List<?>) arrayObject).size();
						} else if (arrayObject instanceof String) {
							return ((String) arrayObject).length();
						} else {
							throw new RuntimeException("Variable for length is not an array: " + firstToken);
						}
					} else {
						throw new RuntimeException("Undefined variable name in expression: " + firstToken);
					}
				} else {
					return parseNumber(firstToken);
				}
			}
		}
	}

	/**
	 * Parses a JavaScript number literal.
	 *
	 * @param token
	 *            the number literal
	 * @return an Integer, if the value is integral and fits, otherwise a Double
	 */
	private static Object parseNumber(final String token) {
		if ("NaN".equals(token)) {
			return Double.NaN;
		} else if ("Infinity".equals(token)) {
			return Double.POSITIVE_INFINITY;
		} else if (HEX_NUMBER_PATTERN.matcher(token).matches()) {
			final long value = Long.parseLong(token.substring(2), 16);
			if (value <= Integer.MAX_VALUE) {
				return (int) value;
			} else {
				return (double) value;
			}
		} else if (DECIMAL_NUMBER_PATTERN.matcher(token).matches()) {
			try {
				return Integer.parseInt(token);
			} catch (@SuppressWarnings("unused") final NumberFormatException e) {
				return Double.parseDouble(token);
			}
		} else {
			throw new RuntimeException("Unexpected code in expression: " + token);
		}
	}

	/**
	 * Resolves the escape sequences of a JavaScript string literal.
	 *
	 * @param text
	 *            the content of the string literal without quotes
	 * @return the resolved text
	 */
	static String unescapeStringLiteral(final String text) {
		if (text.indexOf('\\') < 0) {
			return text;
		}
		final StringBuilder result = new StringBuilder(text.length());
		int index = 0;
		while (index < text.length()) {
			final char currentChar = text.charAt(index);
			if (currentChar != '\\' || index + 1 >= text.length()) {
				result.append(currentChar);
				index++;
			} else {
				final char escapedChar = text.charAt(index + 1);
				index += 2;
				switch (escapedChar) {
					case 'n':
						result.append('\n');
						break;
					case 't':
						result.append('\t');
						break;
					case 'r':
						result.append('\r');
						break;
					case 'b':
						result.append('\b');
						break;
					case 'f':
						result.append('\f');
						break;
					case 'v':
						result.append((char) 0x0B);
						break;
					case '0':
						result.append((char) 0x00);
						break;
					case 'u':
						if (index + 4 <= text.length() && isHex(text.substring(index, index + 4))) {
							result.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
							index += 4;
						} else {
							result.append(escapedChar);
						}
						break;
					case 'x':
						if (index + 2 <= text.length() && isHex(text.substring(index, index + 2))) {
							result.append((char) Integer.parseInt(text.substring(index, index + 2), 16));
							index += 2;
						} else {
							result.append(escapedChar);
						}
						break;
					default:
						// Covers \\, \", \' and \/ as well as unknown escapes, which stand for the character itself
						result.append(escapedChar);
						break;
				}
			}
		}
		return result.toString();
	}

	private static boolean isHex(final String text) {
		for (final char character : text.toCharArray()) {
			if (Character.digit(character, 16) < 0) {
				return false;
			}
		}
		return true;
	}

	private List<Object> readMethodParameters(final Context context, final int bracketStartTokenIndex) {
		final int parametersEnd = PacScriptParserUtilities.findClosingBracketToken(expressionTokens, bracketStartTokenIndex);
		final List<Object> methodCallParameters = new ArrayList<>();
		for (final List<String> parameterTokens : splitByCommaOutsideOfBrackets(expressionTokens.subList(bracketStartTokenIndex + 1, parametersEnd))) {
			methodCallParameters.add(new Expression(parameterTokens).execute(context));
		}
		return methodCallParameters;
	}

	/**
	 * Splits tokens at commas, which are not nested in brackets.
	 *
	 * @param tokens
	 *            the tokens
	 * @return the token groups, empty list for empty tokens
	 */
	private static List<List<String>> splitByCommaOutsideOfBrackets(final List<String> tokens) {
		final List<List<String>> groups = new ArrayList<>();
		if (tokens.isEmpty()) {
			return groups;
		}
		int openBrackets = 0;
		int groupStart = 0;
		for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
			final String token = tokens.get(tokenIndex);
			if ("(".equals(token) || "[".equals(token)) {
				openBrackets++;
			} else if (")".equals(token) || "]".equals(token)) {
				openBrackets--;
			} else if (openBrackets == 0 && ",".equals(token)) {
				groups.add(tokens.subList(groupStart, tokenIndex));
				groupStart = tokenIndex + 1;
			}
		}
		groups.add(tokens.subList(groupStart, tokens.size()));
		return groups;
	}

	private static int readArrayIndex(final Context context, final List<String> expressionTokens, final int bracketStartTokenIndex) {
		final int arrayIndexEnd = PacScriptParserUtilities.findClosingBracketToken(expressionTokens, bracketStartTokenIndex);
		if (arrayIndexEnd == -1) {
			throw new RuntimeException("Missing array index closing operator");
		}

		final Object arrayIndexObject = new Expression(expressionTokens.subList(bracketStartTokenIndex + 1, arrayIndexEnd)).execute(context);
		if (arrayIndexObject == null || !(arrayIndexObject instanceof Integer)) {
			throw new RuntimeException("Invalid array index value: " + arrayIndexObject);
		}

		final int arrayIndex = ((Integer) arrayIndexObject).intValue();
		if (arrayIndex < 0) {
			throw new RuntimeException("Invalid array index value: " + arrayIndexObject);
		}
		return arrayIndex;
	}

	private static List<List<String>> readArrayItems(final List<String> expressionTokens) {
		return splitByCommaOutsideOfBrackets(expressionTokens);
	}

	@Override
	public String toString() {
		if (expressionTokens != null && !expressionTokens.isEmpty()) {
			final String firstToken = expressionTokens.get(0);
			if ("break".equals(firstToken)) {
				return "break;";
			} else if ("continue".equals(firstToken)) {
				return "continue;";
			} else {
				String returnValue = "";
				for (final String expressionToken : expressionTokens) {
					if (returnValue.length() > 0) {
						if (!returnValue.endsWith("(") && !returnValue.endsWith("[") && !expressionToken.equals("(") && !expressionToken.equals(")") && !expressionToken.equals("]") && !expressionToken.equals(",")) {
							returnValue += " ";
						}
					}
					returnValue += expressionToken;
				}
				return returnValue;
			}
		} else {
			if (bracketed) {
				return "(" + expression1.toString() + ")";
			} else if (expression1 == null) {
				// Prefix operator
				return operator + expression2.toString();
			} else if (expression2 == null) {
				// Postfix operator
				return expression1.toString() + operator;
			} else {
				return expression1.toString() + " " + operator + " " + expression2.toString();
			}
		}
	}

	private static boolean bothNumbers(final Object a, final Object b) {
		return a instanceof Number && b instanceof Number;
	}

	/**
	 * Returns an optional method call parameter.
	 *
	 * @param methodCallParameters
	 *            the parameters given in the PAC script
	 * @param index
	 *            the parameter index
	 * @return the parameter, or null if fewer parameters were given
	 */
	private static Object getParameter(final List<Object> methodCallParameters, final int index) {
		return index < methodCallParameters.size() ? methodCallParameters.get(index) : null;
	}
}
