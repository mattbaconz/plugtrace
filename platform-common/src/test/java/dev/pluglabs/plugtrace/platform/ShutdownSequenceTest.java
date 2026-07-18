package dev.pluglabs.plugtrace.platform;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShutdownSequenceTest {
    @Test
    void stopsAsyncProducersBeforeClosingPersistentState() {
        List<String> order = new ArrayList<>();

        ShutdownSequence.close(
                () -> order.add("scheduler"),
                () -> order.add("service"));

        assertEquals(List.of("scheduler", "service"), order);
    }
}
