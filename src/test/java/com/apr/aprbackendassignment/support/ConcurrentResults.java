package com.apr.aprbackendassignment.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Collects every worker outcome before a concurrency test reports a failure. */
public final class ConcurrentResults {

    private ConcurrentResults() {
    }

    public static <T> List<T> collect(List<Future<T>> futures, long timeoutSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        List<T> results = new ArrayList<>(futures.size());
        List<Throwable> failures = new ArrayList<>();

        for (int index = 0; index < futures.size(); index++) {
            Future<T> future = futures.get(index);
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                results.add(future.get(remaining, TimeUnit.NANOSECONDS));
            } catch (ExecutionException | TimeoutException error) {
                failures.add(new AssertionError("Worker " + index + " failed", error));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                failures.add(new AssertionError("Interrupted while collecting worker " + index, error));
                break;
            }
        }

        if (!failures.isEmpty()) {
            AssertionError error = new AssertionError("Concurrency workers failed: " + failures.size());
            failures.forEach(error::addSuppressed);
            throw error;
        }
        return results;
    }
}
