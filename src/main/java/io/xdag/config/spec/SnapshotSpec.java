package io.xdag.config.spec;

public interface SnapshotSpec {

    boolean isSnapshotEnabled();

    boolean isSnapshotJ();

    long getSnapshotHeight();

    void setSnapshotHeight(long height);

    void setSnapshotJ(boolean isSnapshotJ);

    void snapshotEnable();

    long getSnapshotTime();

    void setSnapshotTime(long time);
}
