package com.labless.model;

public class InboxBootstrap {
    private final InboxSnapshot snapshot;
    private final MailboxStats stats;
    private final String nextPageToken;

    public InboxBootstrap(InboxSnapshot snapshot, MailboxStats stats, String nextPageToken) {
        this.snapshot = snapshot;
        this.stats = stats;
        this.nextPageToken = nextPageToken;
    }

    public InboxSnapshot getSnapshot() {
        return snapshot;
    }

    public MailboxStats getStats() {
        return stats;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }
}
