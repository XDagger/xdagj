package io.xdag.consensus;

import io.xdag.core.XdagField;
import lombok.Getter;
import lombok.Setter;

public class Task {
    @Getter
    @Setter
    private XdagField[] task;

    @Getter
    @Setter
    private long taskTime;

    @Getter
    @Setter
    private long taskIndex;

    @Override
    public String toString() {
        return "Task:{ tasktime:" + taskTime + "}";
    }
}
