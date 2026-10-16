package com.lewisgibson.spawnbuildrights.storage;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * One thread that runs store writes in order, off the server thread. A command answers the moment
 * the in-memory registry is updated; the write follows here, and a failure is reported through the
 * handler rather than thrown at the player.
 *
 * <p>Owned by the plugin rather than borrowed from the server's scheduler so it is the same on
 * every platform (Folia included) and can be drained on shutdown, which is what makes the last
 * write before a stop land.
 */
public final class StorageWorker {

    private final ExecutorService executor;
    private final Consumer<Throwable> onFailure;

    /**
     * @param onFailure receives any exception a queued write throws
     */
    public StorageWorker(Consumer<Throwable> onFailure) {
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        this.executor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "SpawnBuildRights-storage");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /**
     * Queues a write. Writes run one at a time in submission order.
     *
     * @param write the work to do on the storage thread
     */
    public void submit(Runnable write) {
        Objects.requireNonNull(write, "write");
        try {
            executor.execute(
                    () -> {
                        try {
                            write.run();
                        } catch (RuntimeException ex) {
                            onFailure.accept(ex);
                        }
                    });
        } catch (RejectedExecutionException ex) {
            // Only after drain(): the plugin is stopping and this write arrived too late. It is
            // reported rather than thrown so the caller on the server thread is not the casualty.
            onFailure.accept(ex);
        }
    }

    /**
     * Waits until every write queued so far has run, without stopping the worker. Used by anything
     * that is about to read the store and wants to see what was just written.
     *
     * @param timeout how long to wait
     * @return whether the queue emptied in time
     */
    public boolean flush(Duration timeout) {
        try {
            executor.submit(() -> {}).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | ExecutionException | RejectedExecutionException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Stops accepting writes and waits for the queued ones to finish.
     *
     * @param timeout how long to wait
     * @return whether every queued write finished within the timeout
     */
    public boolean drain(Duration timeout) {
        executor.shutdown();
        try {
            return executor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
