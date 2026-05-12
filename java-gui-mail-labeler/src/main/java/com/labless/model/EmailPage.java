package com.labless.model;

import java.util.List;

public class EmailPage {
    private final List<EmailMessage> emails;
    private final String nextPageToken;

    public EmailPage(List<EmailMessage> emails, String nextPageToken) {
        this.emails = emails;
        this.nextPageToken = nextPageToken;
    }

    public List<EmailMessage> getEmails() {
        return emails;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }
}
