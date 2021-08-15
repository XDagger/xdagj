package io.xdag.config.spec;

public interface SnapshotSpec {

    boolean isSnapshotEnabled();

    long getSnapshotHeight();

    void setSnapshotHeight(long height);

    void snapshotEnable();

    long getSnapshotTime();

    void setSnapshotTime(long time);
}
