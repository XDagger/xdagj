package io.xdag.consensus;

import io.xdag.core.XdagField;
import lombok.Getter;
import lombok.Setter;



@Getter
@Setter
public class Task {
    /**包含两个字段*/
    private XdagField[] task;
    private long taskTime;
    private long taskIndex;

    @Override
    public String toString(){
        return "Task:{ tasktime:"+taskTime+"}";
    }
}
