package com.rxflow.model;

import java.util.ArrayList;
import java.util.List;

public class TaskStateMachineTest {

    static final List<String> failures = new ArrayList<>();
    static int passed = 0;
    static TaskStateMachine sm = new TaskStateMachine();

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
            throw new AssertionError(msg + " \u2014 expected: " + expected + ", got: " + actual);
    }

    static void yes(boolean c, String msg)  { if (!c) throw new AssertionError(msg + " was false"); }
    static void no(boolean c, String msg)   { if (c)  throw new AssertionError(msg + " was true"); }

    static void throws_(Class<? extends Exception> type, Runnable r, String msg) {
        try { r.run(); throw new AssertionError(msg + " \u2014 no exception thrown"); }
        catch (Exception e) {
            if (!type.isInstance(e))
                throw new AssertionError(msg + " \u2014 got " + e.getClass().getSimpleName());
        }
    }

    static Task fresh() { return new Task("test", "{}"); }

    static Task inState(TaskStatus target) {
        Task t = fresh();
        if (target == TaskStatus.PENDING)   return t;
        sm.transition(t, TaskStatus.SCHEDULED);
        if (target == TaskStatus.SCHEDULED) return t;
        sm.transition(t, TaskStatus.RUNNING);
        if (target == TaskStatus.RUNNING)   return t;
        sm.transition(t, target);
        return t;
    }

    public static void main(String[] args) {
        System.out.println("\n\u2500\u2500 Valid transitions \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        run("PENDING \u2192 SCHEDULED", () -> { Task t = fresh(); sm.transition(t, TaskStatus.SCHEDULED); eq(TaskStatus.SCHEDULED, t.getStatus(), "status"); });
        run("SCHEDULED \u2192 RUNNING", () -> { Task t = inState(TaskStatus.SCHEDULED); sm.transition(t, TaskStatus.RUNNING); eq(TaskStatus.RUNNING, t.getStatus(), "status"); });
        run("RUNNING \u2192 COMPLETED", () -> { Task t = inState(TaskStatus.RUNNING); sm.transition(t, TaskStatus.COMPLETED); eq(TaskStatus.COMPLETED, t.getStatus(), "status"); });
        run("RUNNING \u2192 FAILED", () -> { Task t = inState(TaskStatus.RUNNING); sm.transition(t, TaskStatus.FAILED); eq(TaskStatus.FAILED, t.getStatus(), "status"); });
        run("RUNNING \u2192 RETRIED increments retryCount", () -> {
            Task t = inState(TaskStatus.RUNNING);
            eq(0, t.getRetryCount(), "initial retryCount");
            sm.transition(t, TaskStatus.RETRIED);
            eq(TaskStatus.RETRIED, t.getStatus(), "status");
            eq(1, t.getRetryCount(), "retryCount after RETRIED");
        });
        run("RETRIED \u2192 SCHEDULED", () -> { Task t = inState(TaskStatus.RETRIED); sm.transition(t, TaskStatus.SCHEDULED); eq(TaskStatus.SCHEDULED, t.getStatus(), "status"); });
        run("Full happy path PENDING\u2192SCHEDULED\u2192RUNNING\u2192COMPLETED", () -> {
            Task t = fresh();
            sm.transition(t, TaskStatus.SCHEDULED); sm.transition(t, TaskStatus.RUNNING); sm.transition(t, TaskStatus.COMPLETED);
            eq(TaskStatus.COMPLETED, t.getStatus(), "final status");
            yes(sm.isTerminal(t), "isTerminal");
        });
        run("Full retry path ending COMPLETED, retryCount=1", () -> {
            Task t = fresh();
            sm.transition(t, TaskStatus.SCHEDULED); sm.transition(t, TaskStatus.RUNNING);
            sm.transition(t, TaskStatus.RETRIED);
            sm.transition(t, TaskStatus.SCHEDULED); sm.transition(t, TaskStatus.RUNNING);
            sm.transition(t, TaskStatus.COMPLETED);
            eq(TaskStatus.COMPLETED, t.getStatus(), "final status"); eq(1, t.getRetryCount(), "retryCount");
        });

        System.out.println("\n\u2500\u2500 Invalid transitions \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        run("PENDING \u2192 RUNNING throws",          () -> throws_(IllegalStateTransitionException.class, () -> sm.transition(fresh(), TaskStatus.RUNNING),   "PENDING\u2192RUNNING"));
        run("PENDING \u2192 COMPLETED throws",        () -> throws_(IllegalStateTransitionException.class, () -> sm.transition(fresh(), TaskStatus.COMPLETED), "PENDING\u2192COMPLETED"));
        run("SCHEDULED \u2192 COMPLETED throws",      () -> throws_(IllegalStateTransitionException.class, () -> sm.transition(inState(TaskStatus.SCHEDULED), TaskStatus.COMPLETED), "SCHEDULED\u2192COMPLETED"));
        run("COMPLETED \u2192 RUNNING throws",        () -> throws_(IllegalStateTransitionException.class, () -> sm.transition(inState(TaskStatus.COMPLETED), TaskStatus.RUNNING),   "COMPLETED\u2192RUNNING"));
        run("COMPLETED \u2192 FAILED throws",         () -> throws_(IllegalStateTransitionException.class, () -> sm.transition(inState(TaskStatus.COMPLETED), TaskStatus.FAILED),    "COMPLETED\u2192FAILED"));
        run("FAILED \u2192 SCHEDULED throws",         () -> throws_(IllegalStateTransitionException.class, () -> sm.transition(inState(TaskStatus.FAILED), TaskStatus.SCHEDULED),    "FAILED\u2192SCHEDULED"));
        run("FAILED \u2192 RETRIED throws",           () -> throws_(IllegalStateTransitionException.class, () -> sm.transition(inState(TaskStatus.FAILED), TaskStatus.RETRIED),      "FAILED\u2192RETRIED"));
        run("Exception names FROM and TO states", () -> {
            try { sm.transition(fresh(), TaskStatus.RUNNING); throw new AssertionError("no exception"); }
            catch (IllegalStateTransitionException e) {
                yes(e.getMessage().contains("PENDING"), "message has PENDING");
                yes(e.getMessage().contains("RUNNING"), "message has RUNNING");
                eq(TaskStatus.PENDING, e.getFrom(), "getFrom()");
                eq(TaskStatus.RUNNING, e.getTo(),   "getTo()");
            }
        });

        System.out.println("\n\u2500\u2500 Helper methods \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");

        run("isTerminal false for non-terminal states", () -> {
            no(sm.isTerminal(fresh()),                       "PENDING");
            no(sm.isTerminal(inState(TaskStatus.SCHEDULED)), "SCHEDULED");
            no(sm.isTerminal(inState(TaskStatus.RUNNING)),   "RUNNING");
            no(sm.isTerminal(inState(TaskStatus.RETRIED)),   "RETRIED");
        });
        run("isTerminal true for COMPLETED and FAILED", () -> {
            yes(sm.isTerminal(inState(TaskStatus.COMPLETED)), "COMPLETED");
            yes(sm.isTerminal(inState(TaskStatus.FAILED)),    "FAILED");
        });
        run("canTransition does not mutate state", () -> {
            Task t = fresh();
            yes(sm.canTransition(t, TaskStatus.SCHEDULED), "valid transition");
            eq(TaskStatus.PENDING, t.getStatus(), "state must not change");
        });
        run("canTransition false for invalid moves", () -> {
            Task t = fresh();
            no(sm.canTransition(t, TaskStatus.RUNNING),   "PENDING\u2192RUNNING");
            no(sm.canTransition(t, TaskStatus.COMPLETED), "PENDING\u2192COMPLETED");
        });

        System.out.println("\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        System.out.printf("  %d passed,  %d failed%n", passed, failures.size());
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.out.println("    \u2022 " + f));
            System.exit(1);
        }
        System.out.println("  All tests passed \u2713");
    }
}