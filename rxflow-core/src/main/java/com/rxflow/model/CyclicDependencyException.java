package com.rxflow.model;

import java.util.List;

public class CyclicDependencyException extends RuntimeException {
    private final List<String> cycle;
    public CyclicDependencyException(List<String> cycle) {
        super("Cyclic dependency detected: " + String.join(" → ", cycle));
        this.cycle = List.copyOf(cycle);
    }
    public List<String> getCycle() { return cycle; }
}
