package io.xdag.config.spec;

public interface SnapshotSpec {
    boolean isSnapshotEnabled();
    long getSnapshotHeight();
}
