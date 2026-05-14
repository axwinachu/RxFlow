package com.rxflow.pool;

import com.rxflow.model.Task;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class WorkerQueue {
    private final ArrayDeque<Task> deque=new ArrayDeque<>();
    private final ReentrantLock lock=new ReentrantLock();

    public void push(Task task){
        lock.lock();
        try {
            deque.addFirst(task);
        }finally {
            lock.unlock();
        }
    }
    public Optional<Task> pop(){
        lock.lock();
        try{
            return Optional.ofNullable(deque.pollFirst());
        }finally {
            lock.unlock();
        }
    }

    public Optional<Task> trySteal(){
        if (!lock.tryLock()){
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(deque.pollLast());
        }finally {
            lock.unlock();
        }
    }
    public Optional<Task> peekFirst() {
        lock.lock();
        try {
            return Optional.ofNullable(deque.peekFirst());
        } finally {
            lock.unlock();
        }
    }
    public Optional<Task> peekLast() {
        lock.lock();
        try {
            return Optional.ofNullable(deque.peekLast());
        } finally {
            lock.unlock();
        }
    }
    public int size() {
        lock.lock();
        try {
            return deque.size();
        } finally {
            lock.unlock();
        }
    }
    public boolean isEmpty() {
        lock.lock();
        try {
            return deque.isEmpty();
        } finally {
            lock.unlock();
        }
    }
    @Override
    public String toString() {
        lock.lock();
        try {
            return "WorkerQueue{size=" + deque.size() + ", tasks=" + deque + "}";
        } finally {
            lock.unlock();
        }
    }
}
