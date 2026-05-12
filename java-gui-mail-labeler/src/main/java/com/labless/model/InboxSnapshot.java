package com.labless.model;

import java.util.List;

public class InboxSnapshot {
    private final List<EmailMessage> emails;
    private final List<MailboxLabel> labels;

    public InboxSnapshot(List<EmailMessage> emails, List<MailboxLabel> labels) {
        this.emails = emails;
        this.labels = labels;
    }

    public List<EmailMessage> getEmails() {
        return emails;
    }

    public List<MailboxLabel> getLabels() {
        return labels;
    }
}
