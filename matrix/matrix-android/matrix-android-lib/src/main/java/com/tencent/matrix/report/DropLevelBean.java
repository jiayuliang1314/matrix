package com.tencent.matrix.report;

public class DropLevelBean {
//    dropLevelObject.put(DropStatus.DROPPED_FROZEN.name(), dropLevel[DropStatus.DROPPED_FROZEN.index]);
//    dropLevelObject.put(DropStatus.DROPPED_HIGH.name(), dropLevel[DropStatus.DROPPED_HIGH.index]);
//    dropLevelObject.put(DropStatus.DROPPED_MIDDLE.name(), dropLevel[DropStatus.DROPPED_MIDDLE.index]);
//    dropLevelObject.put(DropStatus.DROPPED_NORMAL.name(), dropLevel[DropStatus.DROPPED_NORMAL.index]);
//    dropLevelObject.put(DropStatus.DROPPED_BEST.name(), dropLevel[DropStatus.DROPPED_BEST.index]);
    private int droppedFrozen;
    private int droppedHigh;
    private int droppedMiddle;
    private int droppedNormal;
    private int droppedBest;

    public int getDroppedFrozen() {
        return droppedFrozen;
    }

    public void setDroppedFrozen(int droppedFrozen) {
        this.droppedFrozen = droppedFrozen;
    }

    public int getDroppedHigh() {
        return droppedHigh;
    }

    public void setDroppedHigh(int droppedHigh) {
        this.droppedHigh = droppedHigh;
    }

    public int getDroppedMiddle() {
        return droppedMiddle;
    }

    public void setDroppedMiddle(int droppedMiddle) {
        this.droppedMiddle = droppedMiddle;
    }

    public int getDroppedNormal() {
        return droppedNormal;
    }

    public void setDroppedNormal(int droppedNormal) {
        this.droppedNormal = droppedNormal;
    }

    public int getDroppedBest() {
        return droppedBest;
    }

    public void setDroppedBest(int droppedBest) {
        this.droppedBest = droppedBest;
    }
}
