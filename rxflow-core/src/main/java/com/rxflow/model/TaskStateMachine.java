package com.rxflow.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class TaskStateMachine {
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS;
    static {
        ALLOWED_TRANSITIONS=new EnumMap<>(TaskStatus.class);

        ALLOWED_TRANSITIONS.put(TaskStatus.PENDING, EnumSet.of(TaskStatus.SCHEDULED));

        ALLOWED_TRANSITIONS.put(TaskStatus.SCHEDULED,EnumSet.of(TaskStatus.RUNNING));

        ALLOWED_TRANSITIONS.put(TaskStatus.RUNNING,EnumSet.of(TaskStatus.COMPLETED,TaskStatus.RETRIED,TaskStatus.FAILED));

        ALLOWED_TRANSITIONS.put(TaskStatus.RETRIED,EnumSet.of(TaskStatus.SCHEDULED));

        ALLOWED_TRANSITIONS.put(TaskStatus.COMPLETED,EnumSet.noneOf(TaskStatus.class));

        ALLOWED_TRANSITIONS.put(TaskStatus.FAILED,EnumSet.noneOf(TaskStatus.class));
    }

    public void transition(Task task,TaskStatus newStatus){
        synchronized (task){
            TaskStatus current=task.getStatus();

            Set<TaskStatus> allowed=ALLOWED_TRANSITIONS.get(current);

            if(!allowed.contains(newStatus)){
                throw new IllegalStateTransitionException(task.getId(),current,newStatus);
            }
            if (newStatus==TaskStatus.RETRIED){
                task.incrementRetryCount();
            }
            task.setStatus(newStatus);
        }
    }

    public boolean isTerminal(Task task){
        return task.getStatus()==TaskStatus.COMPLETED || task.getStatus()==TaskStatus.FAILED;
    }

    public boolean canTransition(Task task,TaskStatus newStatus){
        return ALLOWED_TRANSITIONS.get(task.getStatus()).contains(newStatus);
    }
}
