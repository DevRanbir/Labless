package com.labless.model;

public class MailboxLabel {
    private final String name;
    private final int messageCount;
    private final boolean system;

    public MailboxLabel(String name, int messageCount, boolean system) {
        this.name = name;
        this.messageCount = messageCount;
        this.system = system;
    }

    public String getName() {
        return name;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public boolean isSystem() {
        return system;
    }
}
