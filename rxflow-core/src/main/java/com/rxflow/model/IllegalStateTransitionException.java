package com.rxflow.model;

/**
 * Thrown when code tries to move a Task into a state
 * that isn't reachable from its current state.
 *
 * Example: trying to transition COMPLETED → RUNNING.
 * This should never happen in correct code — if you see this
 * in production, something has seriously gone wrong.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final TaskStatus from;
    private final TaskStatus to;

    public IllegalStateTransitionException(String taskId, TaskStatus from, TaskStatus to) {
        super(String.format(
                "Task '%s': illegal transition %s → %s",
                taskId, from, to
        ));
        this.from = from;
        this.to   = to;
    }

    public TaskStatus getFrom() { return from; }
    public TaskStatus getTo()   { return to; }
}