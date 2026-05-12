package com.labless.gmail;

import com.labless.model.EmailMessage;
import com.labless.model.EmailPage;
import com.labless.model.MailboxStats;
import com.labless.model.MailboxLabel;

import java.util.List;

public interface GmailClient {
    List<EmailMessage> fetchEmails(String query, int maxResults);
    EmailPage fetchEmailPage(String query, String pageToken, int pageSize);
    List<MailboxLabel> fetchLabels();
    MailboxStats fetchMailboxStats(String query);
    void ensureLabel(String labelName);
    void applyLabels(String emailId, List<String> labels);
    void applySingleManagedLabel(String emailId, String targetLabel, List<String> managedLabels);
    void removeFromInbox(String emailId);

    default String getProfileImageUrl() {
        return null;
    }
}
