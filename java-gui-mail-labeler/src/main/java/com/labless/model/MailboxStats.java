package com.labless.model;

public class MailboxStats {
    private final String emailAddress;
    private final long totalMessages;
    private final long totalThreads;
    private final long inboxMessages;
    private final long unreadMessages;
    private final long queryMessageCount;

    public MailboxStats(
        String emailAddress,
        long totalMessages,
        long totalThreads,
        long inboxMessages,
        long unreadMessages,
        long queryMessageCount
    ) {
        this.emailAddress = emailAddress;
        this.totalMessages = totalMessages;
        this.totalThreads = totalThreads;
        this.inboxMessages = inboxMessages;
        this.unreadMessages = unreadMessages;
        this.queryMessageCount = queryMessageCount;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public long getTotalThreads() {
        return totalThreads;
    }

    public long getInboxMessages() {
        return inboxMessages;
    }

    public long getUnreadMessages() {
        return unreadMessages;
    }

    public long getQueryMessageCount() {
        return queryMessageCount;
    }
}
