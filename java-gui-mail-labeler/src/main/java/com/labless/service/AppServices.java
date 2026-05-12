package com.labless.service;

import com.labless.database.DatabaseManager;
import com.labless.gmail.GmailClient;
import com.labless.gmail.GoogleGmailClient;
import com.labless.gmail.InMemoryGmailClient;
import com.labless.llm.LlmService;
import com.labless.llm.LlmServiceFactory;
import com.labless.model.AppConfig;
import com.labless.model.CachedInboxData;
import com.labless.model.EmailPage;
import com.labless.model.EmailMessage;
import com.labless.model.InboxBootstrap;
import com.labless.model.InboxSnapshot;
import com.labless.model.MailboxStats;
import com.labless.model.MailboxLabel;
import com.labless.processor.EmailProcessor;
import com.labless.processor.TransactionDetector;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AppServices implements AutoCloseable {
    private final DatabaseManager databaseManager;
    private final GmailClient gmailClient;
    private final EmailProcessor emailProcessor;

    private AppServices(DatabaseManager databaseManager, GmailClient gmailClient, EmailProcessor emailProcessor) {
        this.databaseManager = databaseManager;
        this.gmailClient = gmailClient;
        this.emailProcessor = emailProcessor;
    }

    public static AppServices fromConfig(AppConfig config) throws SQLException {
        DatabaseManager databaseManager = new DatabaseManager(config.getDatabasePath());
        GmailClient gmailClient;
        if (config.isOnboardingCompleted()) {
            try {
                gmailClient = new GoogleGmailClient();
            } catch (Exception ex) {
                throw new SQLException("Unable to connect to Gmail API: " + ex.getMessage(), ex);
            }
        } else {
            gmailClient = new InMemoryGmailClient();
        }
        LlmService llmService = LlmServiceFactory.create(config);
        TransactionDetector detector = new TransactionDetector();
        EmailProcessor processor = new EmailProcessor(gmailClient, llmService, databaseManager, detector);
        return new AppServices(databaseManager, gmailClient, processor);
    }

    public EmailProcessor getEmailProcessor() {
        return emailProcessor;
    }
    
    public GmailClient getGmailClient() {
        return gmailClient;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public List<EmailMessage> fetchInboxEmails(String query, int maxResults) {
        return gmailClient.fetchEmails(query, maxResults);
    }

    public List<MailboxLabel> fetchLabels() {
        return gmailClient.fetchLabels();
    }

    public MailboxStats fetchMailboxStats(String query) {
        return gmailClient.fetchMailboxStats(query);
    }

    public EmailPage fetchInboxPage(String query, String pageToken, int pageSize) {
        return gmailClient.fetchEmailPage(query, pageToken, pageSize);
    }

    public InboxBootstrap fetchInboxBootstrap(String query, int initialCount) {
        MailboxStats stats = gmailClient.fetchMailboxStats(query);
        List<MailboxLabel> labels = gmailClient.fetchLabels();
        EmailPage firstPage = gmailClient.fetchEmailPage(query, null, initialCount);
        InboxSnapshot snapshot = new InboxSnapshot(firstPage.getEmails(), labels);
        return new InboxBootstrap(snapshot, stats, firstPage.getNextPageToken());
    }

    public InboxSnapshot fetchInboxSnapshot(String query, int maxResults) {
        List<EmailMessage> emails = gmailClient.fetchEmails(query, maxResults);
        List<MailboxLabel> labels = gmailClient.fetchLabels();
        return new InboxSnapshot(emails, labels);
    }

    public String getProfileImageUrl() {
        return gmailClient.getProfileImageUrl();
    }

    public void saveInboxCache(String query, InboxBootstrap bootstrap, boolean fullyLoaded) {
        try {
            databaseManager.saveInboxCache(query, bootstrap, fullyLoaded);
        } catch (SQLException ignored) {
        }
    }

    public Optional<CachedInboxData> loadInboxCache(String query) {
        try {
            return databaseManager.loadInboxCache(query);
        } catch (SQLException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws SQLException {
        databaseManager.close();
    }
}
