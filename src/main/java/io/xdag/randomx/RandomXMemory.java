package io.xdag.randomx;


import lombok.Data;

@Data
public class RandomXMemory {

    protected byte[] seed;
    protected long seedHeight;
    protected long seedTime;
    protected long switchTime;
    protected int isSwitched;
    protected long rxCache;
    protected long rxDataset;
    protected long poolVm;
    protected long blockVm;

    public RandomXMemory() {
        this.switchTime = -1;
        this.isSwitched = -1;
    }
}
