package com.labless.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppConfig {
    private LlmConfig llm;
    private List<String> categories;
    private int batchSize;
    private String gmailQuery;
    private String databasePath;
    private boolean dryRun;
    private boolean onboardingCompleted;

    public static AppConfig defaults() {
        AppConfig config = new AppConfig();
        config.llm = new LlmConfig();
        config.categories = new ArrayList<>(Arrays.asList(
            "Bills & Payments",
            "Personal",
            "Work",
            "Marketing",
            "Newsletters",
            "Low quality",
            "Notifications",
            "Other"
        ));
        config.batchSize = 20;
        config.gmailQuery = "is:unread";
        config.databasePath = "data/email_pipeline.db";
        config.dryRun = true;
        config.onboardingCompleted = false;
        return config;
    }

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getGmailQuery() {
        return gmailQuery;
    }

    public void setGmailQuery(String gmailQuery) {
        this.gmailQuery = gmailQuery;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public void setOnboardingCompleted(boolean onboardingCompleted) {
        this.onboardingCompleted = onboardingCompleted;
    }
}
