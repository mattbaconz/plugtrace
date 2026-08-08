package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StackTraceFormatterTest {
    @Test
    void formatCapsFramesAndIncludesCause() {
        Exception root = new Exception("root");
        root.initCause(new IllegalStateException("cause"));
        String stack = StackTraceFormatter.format(root, 8);
        assertTrue(stack.contains("java.lang.Exception"));
        assertTrue(stack.contains("Caused by:"));
        assertTrue(stack.contains("IllegalStateException"));
    }

    @Test
    void cheapTopKeepsTypeAndFrames() {
        String stack = StackTraceFormatter.cheapTop(new RuntimeException("x"), 3);
        assertTrue(stack.contains("RuntimeException"));
        assertTrue(stack.contains("\tat "));
    }
}
