package dev.pluglabs.plugtrace.paper;

/**
 * Limits how many full stack traces are materialized per second during log storms.
 * Duplicate storms still count via lightweight keys without rebuilding stacks.
 */
final class ExceptionStormBudget {
    private final int maxFullStacksPerSecond;
    private long windowStartMillis;
    private int usedInWindow;

    ExceptionStormBudget(int maxFullStacksPerSecond) {
        this.maxFullStacksPerSecond = Math.max(1, maxFullStacksPerSecond);
        this.windowStartMillis = 0L;
        this.usedInWindow = 0;
    }

    synchronized boolean tryAcquireFullStack(long nowMillis) {
        if (nowMillis - windowStartMillis >= 1_000L) {
            windowStartMillis = nowMillis;
            usedInWindow = 0;
        }
        if (usedInWindow >= maxFullStacksPerSecond) {
            return false;
        }
        usedInWindow++;
        return true;
    }
}
