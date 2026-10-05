package de.soderer.pac.utilities;

import de.soderer.pac.utilities.exception.PacScriptExecutionLimitException;

/**
 * Guards a single PAC script execution (one FindProxyForURL call) against
 * infinite loops and excessive/unbounded recursion.
 * One instance is created per top-level execution and shared (by reference,
 * not copied) across all Context/subContext instances of that execution.
 */
public class ExecutionGuard {
	private static final int DEFAULT_MAX_STEPS = 2_000_000;
	private static final long DEFAULT_MAX_DURATION_MILLIS = 5_000;
	private static final int DEFAULT_MAX_CALL_DEPTH = 200;

	private final long startTimeMillis;
	private final long maxDurationMillis;
	private final int maxSteps;
	private final int maxCallDepth;

	private int stepCount = 0;
	private int callDepth = 0;

	/**
	 * Creates a guard with default limits: 2,000,000 steps, 5 seconds and a call depth of 200.
	 */
	public ExecutionGuard() {
		this(DEFAULT_MAX_STEPS, DEFAULT_MAX_DURATION_MILLIS, DEFAULT_MAX_CALL_DEPTH);
	}

	/**
	 * Creates a guard.
	 *
	 * @param maxSteps
	 *            the maximum number of loop iterations and function calls
	 * @param maxDurationMillis
	 *            the maximum execution time in milliseconds
	 * @param maxCallDepth
	 *            the maximum nesting depth of function calls
	 */
	public ExecutionGuard(final int maxSteps, final long maxDurationMillis, final int maxCallDepth) {
		this.maxSteps = maxSteps;
		this.maxDurationMillis = maxDurationMillis;
		this.maxCallDepth = maxCallDepth;
		startTimeMillis = System.currentTimeMillis();
	}

	/**
	 * Must be called on every loop iteration to enforce step count and
	 * wall-clock time limits.
	 */
	public void checkStep() {
		stepCount++;
		if (stepCount > maxSteps) {
			throw new PacScriptExecutionLimitException("Maximum execution step count exceeded (" + maxSteps + "). Possible infinite loop in PAC script.");
		}
		if (System.currentTimeMillis() - startTimeMillis > maxDurationMillis) {
			throw new PacScriptExecutionLimitException("Maximum execution duration exceeded (" + maxDurationMillis + " ms). Possible infinite loop in PAC script.");
		}
	}

	/**
	 * Must be called when entering a method call, matched by leaveMethodCall()
	 * in a finally-block, to enforce recursion depth limits.
	 */
	public void enterMethodCall() {
		callDepth++;
		if (callDepth > maxCallDepth) {
			throw new PacScriptExecutionLimitException("Maximum call/recursion depth exceeded (" + maxCallDepth + ") in PAC script.");
		}
	}

	/**
	 * Must be called after every function call, see {@link #enterMethodCall()}.
	 */
	public void leaveMethodCall() {
		callDepth--;
	}
}