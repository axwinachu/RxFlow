package com.rxflow.pool;

import com.rxflow.model.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit + concurrency tests for {@link WorkerQueue}.
 *
 * No JUnit dependency — mirrors the style of TaskStateMachineTest.
 *
 * Concurrency test verifies:
 *   - A producer pushes N tasks from the head.
 *   - A stealer concurrently steals from the tail.
 *   - No task is lost and no task is seen twice.
 */
public class WorkerQueueTest {

    static final List<String> failures = new ArrayList<>();
    static int passed = 0;

    static void run(String name, Runnable test) {
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
            throw new AssertionError(msg + " — expected: " + expected + ", got: " + actual);
    }

    static void yes(boolean c, String msg) { if (!c) throw new AssertionError(msg + " was false"); }
    static void no(boolean c, String msg)  { if (c)  throw new AssertionError(msg + " was true"); }

    static Task task(String name) { return new Task(name, "{}"); }

    // -----------------------------------------------------------------------

    public static void main(String[] args) {

        System.out.println("\n── Basic push / pop ─────────────────────────────────");

        run("push then pop returns same task", () -> {
            WorkerQueue q = new WorkerQueue();
            Task t = task("alpha");
            q.push(t);
            Optional<Task> result = q.pop();
            yes(result.isPresent(), "pop should return a task");
            eq(t.getId(), result.get().getId(), "same task id");
        });

        run("pop on empty queue returns empty", () -> {
            WorkerQueue q = new WorkerQueue();
            no(q.pop().isPresent(), "empty queue pop");
        });

        run("push is LIFO — last pushed is first popped", () -> {
            WorkerQueue q = new WorkerQueue();
            Task t1 = task("first");
            Task t2 = task("second");
            Task t3 = task("third");
            q.push(t1); q.push(t2); q.push(t3);
            // head = t3 (newest), tail = t1 (oldest)
            eq(t3.getId(), q.pop().get().getId(), "pop should return t3 (head)");
            eq(t2.getId(), q.pop().get().getId(), "pop should return t2");
            eq(t1.getId(), q.pop().get().getId(), "pop should return t1 (tail)");
            no(q.pop().isPresent(), "queue now empty");
        });

        run("peekFirst does not remove task", () -> {
            WorkerQueue q = new WorkerQueue();
            Task t = task("peek-me");
            q.push(t);
            eq(t.getId(), q.peekFirst().get().getId(), "peekFirst id");
            eq(1, q.size(), "size unchanged after peek");
        });

        System.out.println("\n── trySteal ─────────────────────────────────────────");

        run("trySteal on empty queue returns empty", () -> {
            WorkerQueue q = new WorkerQueue();
            no(q.trySteal().isPresent(), "steal from empty");
        });

        run("trySteal takes from the TAIL (oldest task)", () -> {
            WorkerQueue q = new WorkerQueue();
            Task t1 = task("oldest");
            Task t2 = task("middle");
            Task t3 = task("newest");
            q.push(t1); q.push(t2); q.push(t3);
            // order head→tail: t3, t2, t1
            eq(t1.getId(), q.trySteal().get().getId(), "steal should grab oldest (tail)");
            eq(2, q.size(), "two tasks remain");
        });

        run("push + steal interleaved leaves no duplicates", () -> {
            WorkerQueue q = new WorkerQueue();
            Task a = task("A"); Task b = task("B"); Task c = task("C");
            q.push(a); q.push(b); q.push(c);
            // head→tail: c, b, a
            String stolenId = q.trySteal().get().getId();   // grabs 'a' (tail)
            String poppedId = q.pop().get().getId();        // grabs 'c' (head)
            yes(!stolenId.equals(poppedId), "steal and pop must return different tasks");
            eq(1, q.size(), "one task should remain");
        });

        System.out.println("\n── isEmpty / size ───────────────────────────────────");

        run("isEmpty true on fresh queue", () -> {
            yes(new WorkerQueue().isEmpty(), "fresh queue isEmpty");
        });

        run("size tracks push and pop correctly", () -> {
            WorkerQueue q = new WorkerQueue();
            eq(0, q.size(), "initial size");
            q.push(task("x")); eq(1, q.size(), "after push");
            q.push(task("y")); eq(2, q.size(), "after second push");
            q.pop();           eq(1, q.size(), "after pop");
            q.trySteal();      eq(0, q.size(), "after steal");
        });

        System.out.println("\n── Concurrency: producer + stealer ──────────────────");

        run("concurrent push + steal — no lost or duplicate tasks", () -> {
            final int TASK_COUNT = 5000;
            WorkerQueue q = new WorkerQueue();

            List<String> seen = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger stolen = new AtomicInteger();
            AtomicInteger popped = new AtomicInteger();

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch done  = new CountDownLatch(2);

            // Producer: pushes TASK_COUNT tasks, then drains its own head
            Thread producer = new Thread(() -> {
                ready.countDown();
                try { ready.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                for (int i = 0; i < TASK_COUNT; i++) {
                    q.push(task("task-" + i));
                }
                Optional<Task> t;
                while ((t = q.pop()).isPresent()) {
                    seen.add(t.get().getId());
                    popped.incrementAndGet();
                }
                done.countDown();
            });

            // Stealer: spins stealing from the tail while the producer pushes
            Thread stealer = new Thread(() -> {
                ready.countDown();
                try { ready.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                int attempts = 0;
                while (attempts < TASK_COUNT * 5) {
                    Optional<Task> t = q.trySteal();
                    if (t.isPresent()) {
                        seen.add(t.get().getId());
                        stolen.incrementAndGet();
                        attempts = 0;
                    } else {
                        attempts++;
                        // tiny sleep so the stealer doesn't spin past the producer
                        if (attempts % 100 == 0) {
                            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }
                }
                done.countDown();
            });

            producer.start();
            stealer.start();
            try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            eq(TASK_COUNT, seen.size(), "total tasks accounted for");
            eq(TASK_COUNT, (int) seen.stream().distinct().count(), "no duplicates");
            System.out.printf("       (producer popped %d, stealer stole %d)%n",
                    popped.get(), stolen.get());
        });

        System.out.println("\n─────────────────────────────────────────────────────");
        System.out.printf("  %d passed,  %d failed%n", passed, failures.size());
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.out.println("    • " + f));
            System.exit(1);
        }
        System.out.println("  All tests passed \u2713");
    }
}