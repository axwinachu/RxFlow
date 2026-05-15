package com.rxflow.pool;

import com.rxflow.model.Task;
import com.rxflow.model.TaskStateMachine;
import com.rxflow.model.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;


public class WorkerThread extends Thread{
    private final int index;
    private final WorkerQueue localQueue;
    private final List<WorkerQueue> allQueues;
    private final TaskStateMachine stateMachine;
    private final Consumer<Task> taskRunner;

    //sleep - awake

    private final ReentrantLock sleepLock=new ReentrantLock();
    private final Condition workReady=sleepLock.newCondition();
    private volatile boolean shutdown=false;

    public WorkerThread(int index,WorkerQueue localQueue,List<WorkerQueue> allQueues,TaskStateMachine stateMachine,Consumer<Task> taskRunner){
        super("rxflow-worker-"+index);
        setDaemon(true);
        this.index        = index;
        this.localQueue   = localQueue;
        this.allQueues    = allQueues;
        this.stateMachine = stateMachine;
        this.taskRunner   = taskRunner;
    }
    private Optional<Task> findTask(){
        Optional<Task> task=localQueue.pop();
        if(task.isPresent())return task;
        int n=allQueues.size();
        for(int i=1;i<n;i++){
            WorkerQueue sibling=allQueues.get((index+i)%n);
            task=sibling.trySteal();
            if (task.isPresent()) return task;
        }
        return Optional.empty();
    }
    private void execute(Task task){
        try{
            stateMachine.transition(task, TaskStatus.RUNNING);
            taskRunner.accept(task);
            stateMachine.transition(task,TaskStatus.COMPLETED);
        }catch (Exception ex){
            try {
                stateMachine.transition(task,TaskStatus.FAILED);
            }catch (Exception ignored){

            }
        }
    }
    private void sleep(){
        sleepLock.lock();
        try {
            if(!localQueue.isEmpty() || shutdown) return;

            workReady.await();
        }catch (InterruptedException ex){
            Thread.currentThread().interrupt();
        }finally {
            sleepLock.unlock();
        }
    }
    public void signal() {
        sleepLock.lock();
        try {
            workReady.signal();
        } finally {
            sleepLock.unlock();
        }
    }
    public void shutdown() {
        shutdown = true;
    }
    @Override
    public void run(){
        while (!shutdown){
            Optional<Task> maybeTask=findTask();
            if (maybeTask.isPresent()){
                execute(maybeTask.get());
            }else{
                sleep();
            }
        }
    }
    public WorkerQueue getLocalQueue() {
        return localQueue;
    }
    public int getIndex(){
        return index;
    }

}
