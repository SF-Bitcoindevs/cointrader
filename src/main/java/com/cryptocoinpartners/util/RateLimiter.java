package com.cryptocoinpartners.util;

import org.joda.time.Duration;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.*;


/**
 * Implements an Executor which delays execution until a rate limit is fulfilled.
 *
 * @author Tim Olson
 */
@SuppressWarnings("NullableProblems")
public class RateLimiter implements Executor {

    /**
     * Constructs a RateLimiter with a single-threaded executor
     * @see #RateLimiter(java.util.concurrent.Executor, int, org.joda.time.Duration)
     */
    public RateLimiter(final int invocations, final Duration per) {
        this(null,invocations,per);
    }


    /**
     * Implements the Token Bucket algorithm to provide a maximum number of invocations within each fixed time window.
     * Useful for rate-limiting.  If given a non-null executor, the scheduled runnables are passed to that executor
     * for execution at the rate limit.  If executor is null, a single-threaded executor is used
     * @param executor the Executor which executes the Runnables.  the executor is not called with the runnable until
     *                 the rate limit has been fulfilled
     * @param invocations number of queries allowed during each time window
     * @param per the duration of each time window
     */
    public RateLimiter(Executor executor, final int invocations, final Duration per) {
        if( executor != null ) {
            this.executor = executor;
        }
        else {
            this.executor = Executors.newSingleThreadExecutor();
        }

        // This thread fills the TokenBucket with available requests every time window
        ScheduledThreadPoolExecutor replenisher = new ScheduledThreadPoolExecutor(1);
        replenisher.scheduleAtFixedRate(new Runnable() {
            public void run() {
                int permitsToCreate = invocations - requestsAvailable.availablePermits();
                if( permitsToCreate > 0 ) {
                    synchronized( requestsAvailable ) {
                        // bring the number of requests up to the maximum size per time window
                        requestsAvailable.release(permitsToCreate);
                    }
                }
            }
        }, 0, per.getMillis(), TimeUnit.MILLISECONDS);

        pump = new RunnablePump();
        pump.start();
    }


    public void execute(Runnable runnable) {
        synchronized( waitingRunnables ) {
            waitingRunnables.add(runnable);
        }
        runnableCount.release();
    }


    // This thread waits for a Runnable to be put on the waitingRunnables queue, then waits for the rate limit
    // to be available, then pushes the Runnable to the executor for execution.
    private class RunnablePump extends Thread {
        private RunnablePump() {
            setDaemon(true);
        }


        public void run() {
            boolean running = true;
            while(running) {
                try {
                    runnableCount.acquire(); // wait until there are any scheduled runnables available
                    Runnable runnable;
                    synchronized( waitingRunnables ) {
                        runnable = waitingRunnables.poll();
                    }
                    requestsAvailable.acquire(); // wait until there is request limit available
                    executor.execute(runnable);
                }
                catch( InterruptedException e ) {
                    running = false;
                }
            }
        }

    }


    protected void finalize() throws Throwable {
        pump.interrupt();
        super.finalize();
    }


    private RunnablePump pump;
    private final Semaphore runnableCount = new Semaphore(0);
    private final Semaphore requestsAvailable = new Semaphore(0);
    private final Queue<Runnable> waitingRunnables = new LinkedList<Runnable>();
    private final Executor executor;
}
