package com.pbl4.server.model;

import java.io.Serializable;
import java.util.Objects;

public class ProcessInfoId implements Serializable {

    private Long pid;
    private Long snapshotId;

    public ProcessInfoId() {
    }

    public ProcessInfoId(Long pid, Long snapshotId) {
        this.pid = pid;
        this.snapshotId = snapshotId;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof ProcessInfoId)) return false;

        ProcessInfoId that = (ProcessInfoId) o;

        return Objects.equals(pid, that.pid)
                && Objects.equals(snapshotId, that.snapshotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, snapshotId);
    }
}