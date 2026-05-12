package com.labless.config;

import com.labless.model.AppConfig;

public class ConfigLoadResult {
    private final AppConfig config;
    private final boolean createdNewFile;

    public ConfigLoadResult(AppConfig config, boolean createdNewFile) {
        this.config = config;
        this.createdNewFile = createdNewFile;
    }

    public AppConfig getConfig() {
        return config;
    }

    public boolean isCreatedNewFile() {
        return createdNewFile;
    }
}
