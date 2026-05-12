package com.labless.processor;

import com.labless.database.DatabaseManager;
import com.labless.gmail.GmailClient;
import com.labless.llm.LlmService;
import com.labless.model.CategoryResult;
import com.labless.model.EmailMessage;
import com.labless.model.JobConfig;
import com.labless.model.JobProgress;
import com.labless.model.JobSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmailProcessor {
    private final GmailClient gmailClient;
    private final LlmService llmService;
    private final DatabaseManager databaseManager;
    private final TransactionDetector transactionDetector;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    public EmailProcessor(
        GmailClient gmailClient,
        LlmService llmService,
        DatabaseManager databaseManager,
        TransactionDetector transactionDetector
    ) {
        this.gmailClient = gmailClient;
        this.llmService = llmService;
        this.databaseManager = databaseManager;
        this.transactionDetector = transactionDetector;
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public JobSummary run(JobConfig config, ProgressListener listener) {
        stopRequested.set(false);
        int processed = 0;
        int categorized = 0;
        int labeled = 0;
        int failed = 0;

        List<EmailMessage> emails = gmailClient.fetchEmails(config.getQuery(), config.getBatchSize());
        int total = emails.size();

        for (EmailMessage email : emails) {
            if (stopRequested.get()) {
                break;
            }
            try {
                if (databaseManager.isProcessed(email.getId())) {
                    processed++;
                    listener.onProgress(new JobProgress(
                        processed, categorized, labeled, failed, total,
                        "Skipped already processed email " + email.getId()));
                    continue;
                }

                String category;
                if (transactionDetector.isTransactionEmail(email)) {
                    category = "Bills & Payments";
                } else {
                    CategoryResult result = llmService.categorizeEmail(email, config.getCategories());
                    category = validateCategory(result.getCategory(), config.getCategories());
                    categorized++;
                }

                List<String> labels = new ArrayList<>();
                labels.add(category);

                if (!config.isDryRun()) {
                    gmailClient.applySingleManagedLabel(email.getId(), category, config.getCategories());
                    if (isInboxRemovalCategory(category)) {
                        gmailClient.removeFromInbox(email.getId());
                    }
                    databaseManager.saveProcessed(email.getId(), category, labels, null);
                    labeled++;
                }

                processed++;
                listener.onProgress(new JobProgress(
                    processed, categorized, labeled, failed, total,
                    "Processed " + email.getSubject() + " => " + category));
            } catch (Exception ex) {
                failed++;
                processed++;
                listener.onProgress(new JobProgress(
                    processed, categorized, labeled, failed, total,
                    "Error on " + email.getId() + ": " + ex.getMessage()));
            }
        }

        return new JobSummary(processed, categorized, labeled, failed, stopRequested.get());
    }

    private String validateCategory(String category, List<String> categories) {
        if (category == null || category.isBlank()) {
            return "Other";
        }
        for (String validCategory : categories) {
            if (validCategory.equalsIgnoreCase(category.trim())) {
                return validCategory;
            }
        }
        return "Other";
    }

    private static boolean isInboxRemovalCategory(String category) {
        return "Marketing".equalsIgnoreCase(category)
            || "Newsletters".equalsIgnoreCase(category)
            || "Low quality".equalsIgnoreCase(category);
    }
}
