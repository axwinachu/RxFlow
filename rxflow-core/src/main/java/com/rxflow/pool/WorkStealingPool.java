package com.rxflow.pool;

import com.rxflow.model.Task;
import com.rxflow.model.TaskStateMachine;
import com.rxflow.model.TaskStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class WorkStealingPool {
    private final List<WorkerThread> workers;
    private final List<WorkerQueue> queues;
    private final AtomicInteger submitIndex=new AtomicInteger(0);
    private final TaskStateMachine stateMachine;
    private volatile boolean accepting=true;

    public WorkStealingPool(){
        this(Runtime.getRuntime().availableProcessors());
    }
    public WorkStealingPool(int threadCount){
        this(threadCount,task->{});
    }
    public WorkStealingPool(int threadCount, Consumer<Task> taskRunner) {
        this.stateMachine = new TaskStateMachine();

        List<WorkerQueue>  qs = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) qs.add(new WorkerQueue());
        this.queues = Collections.unmodifiableList(qs);

        List<WorkerThread> ws = new ArrayList<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            ws.add(new WorkerThread(i, qs.get(i), this.queues, stateMachine, taskRunner));
        }
        this.workers = Collections.unmodifiableList(ws);

        workers.forEach(Thread::start);
    }
    public void submit(Task task) {
        if (!accepting) throw new IllegalStateException("Pool is shut down — cannot accept new tasks");
        stateMachine.transition(task, TaskStatus.SCHEDULED);
        int idx = Math.abs(submitIndex.getAndIncrement() % workers.size());
        queues.get(idx).push(task);
        workers.get(idx).signal();
    }
    public void shutdown() {
        accepting = false;
        workers.forEach(WorkerThread::shutdown);
        workers.forEach(WorkerThread::signal);   // wake sleeping workers so they see shutdown=true
    }
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNs = System.nanoTime() + unit.toNanos(timeout);
        for (WorkerThread w : workers) {
            long remainingNs = deadlineNs - System.nanoTime();
            if (remainingNs <= 0) return false;
            w.join(TimeUnit.NANOSECONDS.toMillis(remainingNs));
            if (w.isAlive()) return false;
        }
        return true;
    }
    public int pendingCount() {
        return queues.stream().mapToInt(WorkerQueue::size).sum();
    }
    public int threadCount() {
        return workers.size();
    }
    public boolean isShutdown() {
        return !accepting; }

}
