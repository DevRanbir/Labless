package com.labless.model;

public class JobProgress {
    private final int processed;
    private final int categorized;
    private final int labeled;
    private final int failed;
    private final int total;
    private final String message;

    public JobProgress(int processed, int categorized, int labeled, int failed, int total, String message) {
        this.processed = processed;
        this.categorized = categorized;
        this.labeled = labeled;
        this.failed = failed;
        this.total = total;
        this.message = message;
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

    public int getTotal() {
        return total;
    }

    public String getMessage() {
        return message;
    }
}
