package com.rxflow.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Task {
    private final String id;

    private final String name;

    private final String payload;

    private final Instant createdAt;

    private final List<String> dependsOn;

    private volatile TaskStatus status;

    private volatile  int retryCount;
    public Task(String name,String payload,List<String> dependsOn){
        this.id= UUID.randomUUID().toString();
        this.name=name;
        this.payload=payload;
        this.createdAt=Instant.now();
        this.dependsOn= Collections.unmodifiableList(new ArrayList<>(dependsOn));
        this.status=TaskStatus.PENDING;
        this.retryCount=0;
    }
    public Task(String name,String payload){
        this(name,payload,Collections.emptyList());
    }

    public String getId() {
        return id;
    }
    public String getName(){
        return name;
    }
    public String getPayload(){
        return payload;
    }
    public Instant getCreatedAt(){
        return createdAt;
    }
    public List<String> getDependsOn(){
        return dependsOn;
    }
    public TaskStatus getStatus(){
        return status;
    }
    public int getRetryCount(){
        return retryCount;
    }
    //Package-private setters — only TaskStateMachine should call these

    void setStatus(TaskStatus status){
        this.status=status;
    }
    void  incrementRetryCount(){
        this.retryCount++;
    }
    @Override
    public String toString() {
        return String.format("Task{id='%s', name='%s', status=%s, retries=%d}",
                id.substring(0, 8), name, status, retryCount);
    }
}

