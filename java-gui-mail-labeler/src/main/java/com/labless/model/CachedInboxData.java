package com.labless.model;

public class CachedInboxData {
    private final InboxBootstrap bootstrap;
    private final boolean fullyLoaded;
    private final long cachedAtEpochMillis;

    public CachedInboxData(InboxBootstrap bootstrap, boolean fullyLoaded, long cachedAtEpochMillis) {
        this.bootstrap = bootstrap;
        this.fullyLoaded = fullyLoaded;
        this.cachedAtEpochMillis = cachedAtEpochMillis;
    }

    public InboxBootstrap getBootstrap() {
        return bootstrap;
    }

    public boolean isFullyLoaded() {
        return fullyLoaded;
    }

    public long getCachedAtEpochMillis() {
        return cachedAtEpochMillis;
    }
}
