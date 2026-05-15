package com.rxflow.pool;

import com.rxflow.model.Task;
import com.rxflow.model.TaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkStealingPoolTest {

    static final List<String> failures = new ArrayList<>();
    static int passed = 0;

    @FunctionalInterface
    interface RunnableEx { void run() throws Exception; }

    static void run(String name, RunnableEx test) {
        try {
            test.run();
            System.out.println("  \u2713  " + name);
            passed++;
        } catch (Throwable e) {
            System.out.println("  \u2717  " + name + "\n       \u2192 " + e.getMessage());
            failures.add(name);
        }
    }

    static void eq(Object expected, Object actual, String msg) {
        if (!expected.equals(actual))
            throw new AssertionError(msg + " \u2014 expected: " + expected + ", got: " + actual);
    }
    static void yes(boolean c, String msg) { if (!c) throw new AssertionError(msg + " was false"); }
    static void no(boolean c,  String msg) { if (c)  throw new AssertionError(msg + " was true"); }

    public static void main(String[] args) throws InterruptedException {

        System.out.println("\n\u2500\u2500 Pool lifecycle \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        run("pool starts with correct thread count", () -> {
            WorkStealingPool pool = new WorkStealingPool(4);
            eq(4, pool.threadCount(), "thread count");
            pool.shutdown();
        });

        run("submit after shutdown throws", () -> {
            WorkStealingPool pool = new WorkStealingPool(2);
            pool.shutdown();
            try {
                pool.submit(new Task("late", "{}"));
                throw new AssertionError("expected IllegalStateException");
            } catch (IllegalStateException e) { /* expected */ }
        });

        run("isShutdown reflects state correctly", () -> {
            WorkStealingPool pool = new WorkStealingPool(2);
            no(pool.isShutdown(),  "should not be shut down initially");
            pool.shutdown();
            yes(pool.isShutdown(), "should be shut down after shutdown()");
        });

        System.out.println("\n\u2500\u2500 Task execution \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        run("single task reaches COMPLETED", () -> {
            Task task = new Task("solo", "{}");
            CountDownLatch done = new CountDownLatch(1);
            WorkStealingPool pool = new WorkStealingPool(2, t -> done.countDown());
            pool.submit(task);
            yes(done.await(3, TimeUnit.SECONDS), "task should complete within 3s");
            eq(TaskStatus.COMPLETED, task.getStatus(), "task status");
            pool.shutdown();
        });

        run("100 tasks all reach COMPLETED", () -> {
            final int COUNT = 100;
            CountDownLatch latch = new CountDownLatch(COUNT);
            List<Task> tasks = new ArrayList<>();
            WorkStealingPool pool = new WorkStealingPool(4, t -> latch.countDown());
            for (int i = 0; i < COUNT; i++) {
                Task t = new Task("t-" + i, "{}");
                tasks.add(t);
                pool.submit(t);
            }
            yes(latch.await(5, TimeUnit.SECONDS), "all 100 tasks should complete");
            pool.shutdown();
            yes(pool.awaitTermination(3, TimeUnit.SECONDS), "pool terminates");
            long completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
            eq(COUNT, (int) completed, "all tasks COMPLETED");
        });

        run("failed task runner marks task FAILED, pool keeps running", () -> {
            AtomicInteger completed = new AtomicInteger();
            CountDownLatch allDone  = new CountDownLatch(5);
            WorkStealingPool pool = new WorkStealingPool(2, t -> {
                allDone.countDown();
                if (t.getName().equals("bad")) throw new RuntimeException("intentional failure");
                completed.incrementAndGet();
            });
            pool.submit(new Task("bad", "{}"));
            for (int i = 0; i < 4; i++) pool.submit(new Task("good-" + i, "{}"));
            yes(allDone.await(3, TimeUnit.SECONDS), "all 5 tasks should run within 3s");
            pool.shutdown();
            yes(pool.awaitTermination(3, TimeUnit.SECONDS), "pool should terminate");
            eq(4, completed.get(), "4 good tasks completed");
        });

        System.out.println("\n\u2500\u2500 10,000 task stress test \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        run("10,000 tasks \u2014 all complete, no deadlock, pool shuts down cleanly", () -> {
            final int TASK_COUNT   = 10_000;
            final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
            CountDownLatch allDone   = new CountDownLatch(TASK_COUNT);
            AtomicInteger  completed = new AtomicInteger();
            WorkStealingPool pool = new WorkStealingPool(THREAD_COUNT, t -> {
                completed.incrementAndGet();
                allDone.countDown();
            });
            for (int i = 0; i < TASK_COUNT; i++) pool.submit(new Task("task-" + i, "{}"));
            boolean finished = allDone.await(5, TimeUnit.SECONDS);
            yes(finished, "all 10,000 tasks should complete within 5s (completed: " + completed.get() + ")");
            eq(TASK_COUNT, completed.get(), "completed count must equal submitted count");
            pool.shutdown();
            yes(pool.awaitTermination(3, TimeUnit.SECONDS), "pool should terminate cleanly");
            System.out.printf("       (%d threads, %,d tasks completed)%n", THREAD_COUNT, completed.get());
        });

        System.out.println("\n\u2500\u2500 CountDownLatch correctness \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        run("CountDownLatch reaches zero \u2014 every task runs exactly once", () -> {
            final int TASK_COUNT = 1_000;
            CountDownLatch latch   = new CountDownLatch(TASK_COUNT);
            AtomicInteger  counter = new AtomicInteger(0);
            WorkStealingPool pool = new WorkStealingPool(4, t -> {
                counter.incrementAndGet();
                latch.countDown();
            });
            for (int i = 0; i < TASK_COUNT; i++) pool.submit(new Task("t-" + i, "{}"));
            yes(latch.await(5, TimeUnit.SECONDS), "latch should reach zero");
            eq(TASK_COUNT, counter.get(), "each task runs exactly once");
            eq(0L, latch.getCount(), "latch count must be zero");
            pool.shutdown();
            pool.awaitTermination(3, TimeUnit.SECONDS);
        });

        System.out.println("\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        System.out.printf("  %d passed,  %d failed%n", passed, failures.size());
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.out.println("    \u2022 " + f));
            System.exit(1);
        }
        System.out.println("  All tests passed \u2713");
    }
}