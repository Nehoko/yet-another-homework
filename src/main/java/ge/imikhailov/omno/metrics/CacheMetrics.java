package ge.imikhailov.omno.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.Callable;

@RequiredArgsConstructor
public class CacheMetrics {
    private final Counter l1Hit;
    private final Counter l1Miss;
    private final Counter l2Hit;
    private final Counter l2Miss;
    private final Counter refreshStarted;
    private final Counter refreshSuccess;
    private final Counter refreshFailure;
    private final Counter duplicateSuppressed;
    private final Counter evictions;
    private final Counter clears;
    private final Timer loaderTimer;

    public void l1HitIncrement() {
        l1Hit.increment();
    }

    public void l1MissIncrement() {
        l1Miss.increment();
    }

    public void l2HitIncrement() {
        l2Hit.increment();
    }

    public void l2MissIncrement() {
        l2Miss.increment();
    }

    public void refreshStartedIncrement() {
        refreshStarted.increment();
    }

    public void refreshSuccessIncrement() {
        refreshSuccess.increment();
    }

    public void refreshFailureIncrement() {
        refreshFailure.increment();
    }

    public void duplicateSuppressedIncrement() {
        duplicateSuppressed.increment();
    }

    public void evictionIncrement() {
        evictions.increment();
    }

    public void clearIncrement() {
        clears.increment();
    }

    public <T> T callInTimer(final Callable<T> callable) throws Exception {
        return loaderTimer.recordCallable(callable);
    }
}
