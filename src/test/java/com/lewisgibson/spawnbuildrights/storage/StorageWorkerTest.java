package com.lewisgibson.spawnbuildrights.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StorageWorker")
class StorageWorkerTest {

    @Test
    @DisplayName("runs writes in order on one named thread and drains them on shutdown")
    void runsInOrder() {
        List<String> seen = new CopyOnWriteArrayList<>();
        StorageWorker worker = new StorageWorker(ex -> seen.add("failure"));
        for (int i = 0; i < 20; i++) {
            String item = "w" + i;
            worker.submit(() -> seen.add(item + "@" + Thread.currentThread().getName()));
        }
        assertThat(worker.drain(Duration.ofSeconds(5))).isTrue();
        assertThat(seen).hasSize(20);
        assertThat(seen.get(0)).isEqualTo("w0@SpawnBuildRights-storage");
        assertThat(seen.get(19)).isEqualTo("w19@SpawnBuildRights-storage");
    }

    @Test
    @DisplayName("reports a failing write and keeps going")
    void reportsFailure() {
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<String> seen = new CopyOnWriteArrayList<>();
        StorageWorker worker = new StorageWorker(failures::add);
        worker.submit(
                () -> {
                    throw new IllegalStateException("disk full");
                });
        worker.submit(() -> seen.add("after"));
        assertThat(worker.drain(Duration.ofSeconds(5))).isTrue();
        assertThat(failures)
                .singleElement()
                .extracting(Throwable::getMessage)
                .isEqualTo("disk full");
        assertThat(seen).containsExactly("after");
    }

    @Test
    @DisplayName("flush waits for the queue without stopping the worker")
    void flushKeepsRunning() {
        List<String> seen = new CopyOnWriteArrayList<>();
        StorageWorker worker = new StorageWorker(ex -> {});
        worker.submit(() -> seen.add("first"));
        assertThat(worker.flush(Duration.ofSeconds(5))).isTrue();
        assertThat(seen).containsExactly("first");
        worker.submit(() -> seen.add("second"));
        assertThat(worker.flush(Duration.ofSeconds(5))).isTrue();
        assertThat(seen).containsExactly("first", "second");
        assertThat(worker.drain(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    @DisplayName("flush reports false on a timeout, after shutdown, and when interrupted")
    void flushFailures() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        StorageWorker worker = new StorageWorker(ex -> {});
        worker.submit(
                () -> {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
        assertThat(worker.flush(Duration.ofMillis(50))).isFalse();

        boolean[] result = new boolean[1];
        Thread waiter = new Thread(() -> result[0] = worker.flush(Duration.ofSeconds(5)));
        waiter.start();
        waiter.interrupt();
        waiter.join(5_000);
        assertThat(result[0]).isFalse();

        release.countDown();
        assertThat(worker.drain(Duration.ofSeconds(5))).isTrue();
        assertThat(worker.flush(Duration.ofSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("a write submitted after drain is reported as a failure, not thrown")
    void submitAfterDrain() {
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        StorageWorker worker = new StorageWorker(failures::add);
        assertThat(worker.drain(Duration.ofSeconds(5))).isTrue();
        worker.submit(() -> {});
        assertThat(failures).singleElement().isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    @DisplayName("drain reports false when a write outlives the timeout")
    void drainTimesOut() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        StorageWorker worker = new StorageWorker(ex -> {});
        worker.submit(
                () -> {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
        assertThat(worker.drain(Duration.ofMillis(50))).isFalse();
        release.countDown();
    }

    @Test
    @DisplayName("drain reports false when the waiting thread is interrupted")
    void drainInterrupted() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        StorageWorker worker = new StorageWorker(ex -> {});
        worker.submit(
                () -> {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
        boolean[] result = new boolean[1];
        Thread waiter = new Thread(() -> result[0] = worker.drain(Duration.ofSeconds(5)));
        waiter.start();
        waiter.interrupt();
        waiter.join(5_000);
        assertThat(result[0]).isFalse();
        release.countDown();
    }

    @Test
    @DisplayName("rejects null arguments")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> new StorageWorker(null));
        assertThatNullPointerException().isThrownBy(() -> new StorageWorker(ex -> {}).submit(null));
    }
}
