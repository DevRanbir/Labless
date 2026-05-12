package com.labless.model;

public class JobSummary {
    private final int processed;
    private final int categorized;
    private final int labeled;
    private final int failed;
    private final boolean stopped;

    public JobSummary(int processed, int categorized, int labeled, int failed, boolean stopped) {
        this.processed = processed;
        this.categorized = categorized;
        this.labeled = labeled;
        this.failed = failed;
        this.stopped = stopped;
    }

    public int getProcessed() {
        return processed;
    }

    public int getCategorized() {
        return categorized;
    }

    public int getLabeled() {
        return labeled;
    }

    public int getFailed() {
        return failed;
    }

    public boolean isStopped() {
        return stopped;
    }
}
