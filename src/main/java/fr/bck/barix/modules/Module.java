package fr.bck.barix.modules;

import java.util.List;

public interface Module {
    String id();
    default List<String> dependsOn() { return List.of(); }
    void start();
    void stop();
}

