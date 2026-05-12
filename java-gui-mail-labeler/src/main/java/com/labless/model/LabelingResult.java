package com.labless.model;

public class LabelingResult {
    private final String emailId;
    private final String subject;
    private final String sender;
    private final String category;
    private final String explanation;
    private final boolean success;
    private final String error;
    
    public LabelingResult(String emailId, String subject, String sender, String category, String explanation, boolean success, String error) {
        this.emailId = emailId;
        this.subject = subject;
        this.sender = sender;
        this.category = category;
        this.explanation = explanation;
        this.success = success;
        this.error = error;
    }
    
    public String getEmailId() {
        return emailId;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public String getSender() {
        return sender;
    }
    
    public String getCategory() {
        return category;
    }
    
    public String getExplanation() {
        return explanation;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getError() {
        return error;
    }
}
