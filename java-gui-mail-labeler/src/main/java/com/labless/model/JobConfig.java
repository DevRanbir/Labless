package com.labless.model;

import java.util.List;

public class JobConfig {
    private final List<String> categories;
    private final String query;
    private final int batchSize;
    private final boolean dryRun;

    public JobConfig(List<String> categories, String query, int batchSize, boolean dryRun) {
        this.categories = categories;
        this.query = query;
        this.batchSize = batchSize;
        this.dryRun = dryRun;
    }

    public List<String> getCategories() {
        return categories;
    }

    public String getQuery() {
        return query;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isDryRun() {
        return dryRun;
    }
}
