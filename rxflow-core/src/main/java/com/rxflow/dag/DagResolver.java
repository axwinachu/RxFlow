package com.rxflow.dag;

import com.rxflow.model.Task;

import java.util.*;

public class DagResolver {
    public List<Task> resolve(List<Task> tasks){
        Map<String,Task> taskById= new LinkedHashMap<>();
        for(Task t:tasks){
            taskById.put(t.getId(),t);
        }
        //validate all dependency are their in the taskById;
        for(Task t:tasks){
            for(String depId: t.getDependsOn()){
                if(taskById.containsKey(depId)){
                    throw new IllegalArgumentException( "Task '" + t.getName() + "' depends on unknown task id: " + depId);
                }
            }
        }
//        dependency map and degree count of each task
        Map<String,List<String>> dependents=new HashMap<>();
        Map<String,Integer> inDegree=new HashMap<>();

        for(Task t: tasks){
            inDegree.put(t.getId(),t.getDependsOn().size());
            dependents.putIfAbsent(t.getId(),new ArrayList<>());
        }
        for (Task t:tasks){
            for(String depId:t.getDependsOn()){
                dependents.get(depId).add(t.getId());
            }
        }
    }
}
