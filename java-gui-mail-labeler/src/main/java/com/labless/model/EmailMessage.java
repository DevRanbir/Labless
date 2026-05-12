package com.labless.model;

public class EmailMessage {
    private final String id;
    private final String subject;
    private final String sender;
    private final String date;
    private final String body;
    private final java.util.List<String> labels;
    private boolean unread;

    public EmailMessage(
        String id,
        String subject,
        String sender,
        String date,
        String body,
        java.util.List<String> labels,
        boolean unread
    ) {
        this.id = id;
        this.subject = subject;
        this.sender = sender;
        this.date = date;
        this.body = body;
        this.labels = labels == null ? java.util.List.of() : java.util.List.copyOf(labels);
        this.unread = unread;
    }

    public String getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getSender() {
        return sender;
    }

    public String getDate() {
        return date;
    }

    public String getBody() {
        return body;
    }

    public java.util.List<String> getLabels() {
        return labels;
    }

    public boolean isUnread() {
        return unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }
}
