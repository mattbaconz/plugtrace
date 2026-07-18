package dev.pluglabs.plugtrace.paper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded in-process SSE broadcaster; slow/broken clients are dropped rather than queued. */
final class SseBroker implements AutoCloseable {
    private final int maxSubscribers;
    private final CopyOnWriteArrayList<OutputStream> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    SseBroker(int maxSubscribers) {
        this.maxSubscribers = Math.max(1, maxSubscribers);
    }

    boolean subscribe(OutputStream output) {
        if (output == null || closed.get() || subscribers.size() >= maxSubscribers) return false;
        subscribers.add(output);
        return true;
    }

    void publish(String event, String data) {
        if (closed.get()) return;
        byte[] payload = ("event: " + safeEvent(event) + "\n" + "data: "
                + singleLine(data) + "\n\n").getBytes(StandardCharsets.UTF_8);
        for (OutputStream output : subscribers) {
            try {
                output.write(payload);
                output.flush();
            } catch (IOException e) {
                subscribers.remove(output);
                closeQuietly(output);
            }
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }

    private static String safeEvent(String event) {
        return event == null ? "message" : event.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static String singleLine(String data) {
        return data == null ? "{}" : data.replace("\r", "").replace("\n", "");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        subscribers.forEach(SseBroker::closeQuietly);
        subscribers.clear();
    }

    private static void closeQuietly(OutputStream output) {
        try {
            output.close();
        } catch (IOException ignored) {
            // Already disconnected.
        }
    }
}
