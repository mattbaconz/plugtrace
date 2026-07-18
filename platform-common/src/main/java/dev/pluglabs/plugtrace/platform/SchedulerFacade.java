package dev.pluglabs.plugtrace.platform;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Scheduler abstraction: Paper/Bukkit use BukkitScheduler; Folia uses GlobalRegionScheduler /
 * AsyncScheduler. Never blocks region threads with store I/O — async worker owns DB writes.
 */
public final class SchedulerFacade implements AutoCloseable {
    private final Plugin plugin;
    private final boolean folia;
    private final ExecutorService worker;
    private final ExecutorService loopWorker;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Logger logger;

    private SchedulerFacade(Plugin plugin, boolean folia, Logger logger) {
        this.plugin = plugin;
        this.folia = folia;
        this.logger = logger;
        this.worker = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "plugtrace-worker");
            t.setDaemon(true);
            return t;
        });
        this.loopWorker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "plugtrace-event-loop");
            t.setDaemon(true);
            return t;
        });
    }

    public static SchedulerFacade create(Plugin plugin, String serverName, String versionString) {
        boolean folia = FoliaDetect.isFolia(serverName, versionString);
        return new SchedulerFacade(plugin, folia, plugin.getLogger());
    }

    public boolean isFolia() {
        return folia;
    }

    /** Immutable event envelopes / DB work — always on dedicated worker, never region/main. */
    public void runAsync(Runnable task) {
        if (closed.get()) {
            return;
        }
        worker.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.warning("PlugTrace async task failed: " + e.getMessage());
            }
        });
    }

    /**
     * Long-running loop on the async worker (e.g. event drain). Caller must not block region/main threads.
     */
    public void startWorkerLoop(Runnable loop) {
        if (!closed.get()) {
            loopWorker.execute(loop);
        }
    }

    public ExecutorService worker() {
        return worker;
    }

    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Delayed follow-up that may touch Bukkit API.
     * Folia: GlobalRegionScheduler; Paper/Bukkit: BukkitScheduler sync.
     */
    public void runDelayedSync(Runnable task, long delayTicks) {
        if (closed.get() || plugin == null || !plugin.isEnabled()) {
            return;
        }
        if (folia) {
            try {
                invokeFoliaScheduler("runDelayed", task, Math.max(1L, delayTicks), 0L);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                logger.warning("Folia GlobalRegionScheduler unavailable, falling back: " + e.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
    }

    public BukkitTask runRepeatingSync(Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            try {
                invokeFoliaScheduler("runAtFixedRate", task, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                return null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("Folia GlobalRegionScheduler unavailable", e);
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    private void invokeFoliaScheduler(String methodName, Runnable task, long delayTicks, long periodTicks)
            throws ReflectiveOperationException {
        Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
        Consumer<Object> consumer = ignored -> {
            if (!closed.get()) {
                task.run();
            }
        };
        if (periodTicks > 0L) {
            scheduler.getClass().getMethod(methodName, Plugin.class, Consumer.class, long.class, long.class)
                    .invoke(scheduler, plugin, consumer, delayTicks, periodTicks);
        } else {
            scheduler.getClass().getMethod(methodName, Plugin.class, Consumer.class, long.class)
                    .invoke(scheduler, plugin, consumer, delayTicks);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        worker.shutdownNow();
        loopWorker.shutdownNow();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
            loopWorker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
