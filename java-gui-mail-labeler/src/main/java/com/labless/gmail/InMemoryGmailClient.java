package com.labless.gmail;

import com.labless.model.EmailMessage;
import com.labless.model.EmailPage;
import com.labless.model.MailboxStats;
import com.labless.model.MailboxLabel;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InMemoryGmailClient implements GmailClient {
    private final List<EmailMessage> sampleEmails;
    private final Set<String> labels = new HashSet<>();

    public InMemoryGmailClient() {
        sampleEmails = new ArrayList<>();
        seed();
    }

    private void seed() {
        String now = ZonedDateTime.now().toString();
        sampleEmails.add(new EmailMessage(
            "m-1001",
            "Your electricity bill for April",
            "billing@utility-bank.com",
            now,
            "Statement generated. Amount due INR 2,450 before 15th.",
            List.of("Bills & Payments"),
            true
        ));
        sampleEmails.add(new EmailMessage(
            "m-1002",
            "Weekend trip plan",
            "friend@example.com",
            now,
            "Let us finalize Friday evening travel and hotel booking.",
            List.of("Personal"),
            true
        ));
        sampleEmails.add(new EmailMessage(
            "m-1003",
            "Flash sale starts now",
            "offers@shopmart.com",
            now,
            "Limited time deal. Big discount for all categories.",
            List.of("Promotions"),
            false
        ));
        sampleEmails.add(new EmailMessage(
            "m-1004",
            "Payroll update for this month",
            "hr@company.com",
            now,
            "Your compensation statement and tax breakdown is attached.",
            List.of("Work"),
            false
        ));
    }

    @Override
    public List<EmailMessage> fetchEmails(String query, int maxResults) {
        int end = Math.min(maxResults, sampleEmails.size());
        return new ArrayList<>(sampleEmails.subList(0, end));
    }

    @Override
    public EmailPage fetchEmailPage(String query, String pageToken, int pageSize) {
        int pageIndex = 0;
        if (pageToken != null && !pageToken.isBlank()) {
            try {
                pageIndex = Integer.parseInt(pageToken);
            } catch (NumberFormatException ignore) {
                pageIndex = 0;
            }
        }
        int start = pageIndex * pageSize;
        if (start >= sampleEmails.size()) {
            return new EmailPage(List.of(), null);
        }
        int end = Math.min(start + pageSize, sampleEmails.size());
        String nextToken = end >= sampleEmails.size() ? null : String.valueOf(pageIndex + 1);
        return new EmailPage(new ArrayList<>(sampleEmails.subList(start, end)), nextToken);
    }

    @Override
    public List<MailboxLabel> fetchLabels() {
        List<MailboxLabel> result = new ArrayList<>();
        result.add(new MailboxLabel("INBOX", sampleEmails.size(), true));
        result.add(new MailboxLabel("SENT", 0, true));
        result.add(new MailboxLabel("TRASH", 0, true));
        for (String label : labels) {
            result.add(new MailboxLabel(label, 0, false));
        }
        return result;
    }

    @Override
    public MailboxStats fetchMailboxStats(String query) {
        long count = sampleEmails.size();
        return new MailboxStats("mock@localhost", count, count, count, count, count);
    }

    @Override
    public void ensureLabel(String labelName) {
        labels.add(labelName);
    }

    @Override
    public void applyLabels(String emailId, List<String> labelsToApply) {
        labels.addAll(labelsToApply);
    }

    @Override
    public void applySingleManagedLabel(String emailId, String targetLabel, List<String> managedLabels) {
        if (managedLabels != null) {
            labels.removeIf(managedLabels::contains);
        }
        if (targetLabel != null && !targetLabel.isBlank()) {
            labels.add(targetLabel);
        }
    }

    @Override
    public void removeFromInbox(String emailId) {
        // No-op for in-memory mock.
    }
}
