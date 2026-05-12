package com.labless.database;

import com.labless.model.CachedInboxData;
import com.labless.model.EmailMessage;
import com.labless.model.InboxBootstrap;
import com.labless.model.InboxSnapshot;
import com.labless.model.MailboxLabel;
import com.labless.model.MailboxStats;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseManager implements AutoCloseable {
    private static final String LABELS_SEPARATOR = "\u001F";
    private final Connection connection;

    public DatabaseManager(String databasePath) throws SQLException {
        try {
            Path parent = Path.of(databasePath).toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception ignored) {
        }

        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS processed_emails (
                    email_id TEXT PRIMARY KEY,
                    category TEXT NOT NULL,
                    labels TEXT NOT NULL,
                    explanation TEXT,
                    processed_at TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS cached_emails (
                    query_key TEXT NOT NULL,
                    email_id TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    date_text TEXT NOT NULL,
                    body TEXT NOT NULL,
                    labels_text TEXT NOT NULL DEFAULT '',
                    unread INTEGER NOT NULL,
                    sort_index INTEGER NOT NULL,
                    PRIMARY KEY (query_key, email_id)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS cached_labels (
                    query_key TEXT NOT NULL,
                    name TEXT NOT NULL,
                    message_count INTEGER NOT NULL,
                    is_system INTEGER NOT NULL,
                    PRIMARY KEY (query_key, name)
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS cached_inbox_state (
                    query_key TEXT PRIMARY KEY,
                    email_address TEXT,
                    total_messages INTEGER NOT NULL,
                    total_threads INTEGER NOT NULL,
                    inbox_messages INTEGER NOT NULL,
                    unread_messages INTEGER NOT NULL,
                    query_message_count INTEGER NOT NULL,
                    next_page_token TEXT,
                    fully_loaded INTEGER NOT NULL,
                    cached_at_epoch_ms INTEGER NOT NULL
                )
                """);
            ensureColumnExists("cached_emails", "labels_text", "TEXT NOT NULL DEFAULT ''");
            ensureColumnExists("processed_emails", "explanation", "TEXT");
            backfillCachedEmailLabels();
        }
    }

    /**
     * One-time backfill: for any cached_emails row with empty labels_text, copy the
     * category from processed_emails as a fallback label so filters work on old cache.
     */
    private void backfillCachedEmailLabels() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                UPDATE cached_emails
                SET labels_text = (
                    SELECT pe.labels
                    FROM processed_emails pe
                    WHERE pe.email_id = cached_emails.email_id
                    LIMIT 1
                )
                WHERE (labels_text IS NULL OR labels_text = '')
                  AND EXISTS (
                    SELECT 1 FROM processed_emails pe WHERE pe.email_id = cached_emails.email_id
                  )
                """);
        }
    }

    private void ensureColumnExists(String tableName, String columnName, String columnDef) throws SQLException {
        boolean exists = false;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                String current = rs.getString("name");
                if (columnName.equalsIgnoreCase(current)) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef);
            }
        }
    }

    public boolean isProcessed(String emailId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT email_id FROM processed_emails WHERE email_id = ?")) {
            statement.setString(1, emailId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void saveProcessed(String emailId, String category, List<String> labels, String explanation) throws SQLException {
        String labelString = String.join(",", labels);
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT OR REPLACE INTO processed_emails (email_id, category, labels, explanation, processed_at)
            VALUES (?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, emailId);
            statement.setString(2, category);
            statement.setString(3, labelString);
            statement.setString(4, explanation);
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }
    }
    
    /**
     * Get recent labeling history (last N processed emails).
     */
    public List<ProcessedEmailRecord> getRecentLabelingHistory(int limit) throws SQLException {
        List<ProcessedEmailRecord> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT email_id, category, labels, explanation, processed_at
            FROM processed_emails
            ORDER BY processed_at DESC
            LIMIT ?
            """)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    results.add(new ProcessedEmailRecord(
                        rs.getString("email_id"),
                        rs.getString("category"),
                        rs.getString("labels"),
                        rs.getString("explanation"),
                        rs.getString("processed_at")
                    ));
                }
            }
        }
        return results;
    }
    
    /**
     * Simple record to hold processed email data.
     */
    public static class ProcessedEmailRecord {
        private final String emailId;
        private final String category;
        private final String labels;
        private final String explanation;
        private final String processedAt;
        
        public ProcessedEmailRecord(String emailId, String category, String labels, String explanation, String processedAt) {
            this.emailId = emailId;
            this.category = category;
            this.labels = labels;
            this.explanation = explanation;
            this.processedAt = processedAt;
        }
        
        public String getEmailId() { return emailId; }
        public String getCategory() { return category; }
        public String getLabels() { return labels; }
        public String getExplanation() { return explanation; }
        public String getProcessedAt() { return processedAt; }
    }

    public void saveInboxCache(String query, InboxBootstrap bootstrap, boolean fullyLoaded) throws SQLException {
        if (bootstrap == null || bootstrap.getSnapshot() == null) {
            return;
        }
        String queryKey = query == null ? "" : query;
        MailboxStats stats = bootstrap.getStats() == null
            ? new MailboxStats("", 0, 0, 0, 0, 0)
            : bootstrap.getStats();

        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement deleteEmails = connection.prepareStatement(
                "DELETE FROM cached_emails WHERE query_key = ?"
            ); PreparedStatement deleteLabels = connection.prepareStatement(
                "DELETE FROM cached_labels WHERE query_key = ?"
            ); PreparedStatement deleteState = connection.prepareStatement(
                "DELETE FROM cached_inbox_state WHERE query_key = ?"
            )) {
                deleteEmails.setString(1, queryKey);
                deleteEmails.executeUpdate();
                deleteLabels.setString(1, queryKey);
                deleteLabels.executeUpdate();
                deleteState.setString(1, queryKey);
                deleteState.executeUpdate();
            }

            List<EmailMessage> emails = bootstrap.getSnapshot().getEmails() == null
                ? List.of()
                : bootstrap.getSnapshot().getEmails();
            try (PreparedStatement insertEmail = connection.prepareStatement("""
                INSERT INTO cached_emails (query_key, email_id, subject, sender, date_text, body, labels_text, unread, sort_index)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                int index = 0;
                for (EmailMessage email : emails) {
                    if (email == null || email.getId() == null || email.getId().isBlank()) {
                        continue;
                    }
                    insertEmail.setString(1, queryKey);
                    insertEmail.setString(2, email.getId());
                    insertEmail.setString(3, email.getSubject() == null ? "" : email.getSubject());
                    insertEmail.setString(4, email.getSender() == null ? "" : email.getSender());
                    insertEmail.setString(5, email.getDate() == null ? "" : email.getDate());
                    insertEmail.setString(6, email.getBody() == null ? "" : email.getBody());
                    insertEmail.setString(7, encodeLabels(email.getLabels()));
                    insertEmail.setInt(8, email.isUnread() ? 1 : 0);
                    insertEmail.setInt(9, index++);
                    insertEmail.addBatch();
                }
                insertEmail.executeBatch();
            }

            List<MailboxLabel> labels = bootstrap.getSnapshot().getLabels() == null
                ? List.of()
                : bootstrap.getSnapshot().getLabels();
            try (PreparedStatement insertLabel = connection.prepareStatement("""
                INSERT INTO cached_labels (query_key, name, message_count, is_system)
                VALUES (?, ?, ?, ?)
                """)) {
                for (MailboxLabel label : labels) {
                    if (label == null || label.getName() == null || label.getName().isBlank()) {
                        continue;
                    }
                    insertLabel.setString(1, queryKey);
                    insertLabel.setString(2, label.getName());
                    insertLabel.setInt(3, label.getMessageCount());
                    insertLabel.setInt(4, label.isSystem() ? 1 : 0);
                    insertLabel.addBatch();
                }
                insertLabel.executeBatch();
            }

            try (PreparedStatement insertState = connection.prepareStatement("""
                INSERT INTO cached_inbox_state (
                    query_key, email_address, total_messages, total_threads, inbox_messages, unread_messages,
                    query_message_count, next_page_token, fully_loaded, cached_at_epoch_ms
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                insertState.setString(1, queryKey);
                insertState.setString(2, stats.getEmailAddress());
                insertState.setLong(3, stats.getTotalMessages());
                insertState.setLong(4, stats.getTotalThreads());
                insertState.setLong(5, stats.getInboxMessages());
                insertState.setLong(6, stats.getUnreadMessages());
                insertState.setLong(7, stats.getQueryMessageCount());
                insertState.setString(8, bootstrap.getNextPageToken());
                insertState.setInt(9, fullyLoaded ? 1 : 0);
                insertState.setLong(10, Instant.now().toEpochMilli());
                insertState.executeUpdate();
            }

            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public Optional<CachedInboxData> loadInboxCache(String query) throws SQLException {
        String queryKey = query == null ? "" : query;
        MailboxStats stats;
        String nextPageToken;
        boolean fullyLoaded;
        long cachedAtEpochMs;

        try (PreparedStatement stateStmt = connection.prepareStatement("""
            SELECT email_address, total_messages, total_threads, inbox_messages, unread_messages,
                   query_message_count, next_page_token, fully_loaded, cached_at_epoch_ms
            FROM cached_inbox_state
            WHERE query_key = ?
            """)) {
            stateStmt.setString(1, queryKey);
            try (ResultSet rs = stateStmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                stats = new MailboxStats(
                    rs.getString("email_address"),
                    rs.getLong("total_messages"),
                    rs.getLong("total_threads"),
                    rs.getLong("inbox_messages"),
                    rs.getLong("unread_messages"),
                    rs.getLong("query_message_count")
                );
                nextPageToken = rs.getString("next_page_token");
                fullyLoaded = rs.getInt("fully_loaded") == 1;
                cachedAtEpochMs = rs.getLong("cached_at_epoch_ms");
            }
        }

        List<EmailMessage> emails = new ArrayList<>();
        try (PreparedStatement emailStmt = connection.prepareStatement("""
            SELECT email_id, subject, sender, date_text, body, labels_text, unread
            FROM cached_emails
            WHERE query_key = ?
            ORDER BY sort_index ASC
            """)) {
            emailStmt.setString(1, queryKey);
            try (ResultSet rs = emailStmt.executeQuery()) {
                while (rs.next()) {
                    emails.add(new EmailMessage(
                        rs.getString("email_id"),
                        rs.getString("subject"),
                        rs.getString("sender"),
                        rs.getString("date_text"),
                        rs.getString("body"),
                        decodeLabels(rs.getString("labels_text")),
                        rs.getInt("unread") == 1
                    ));
                }
            }
        }

        List<MailboxLabel> labels = new ArrayList<>();
        try (PreparedStatement labelStmt = connection.prepareStatement("""
            SELECT name, message_count, is_system
            FROM cached_labels
            WHERE query_key = ?
            ORDER BY name COLLATE NOCASE
            """)) {
            labelStmt.setString(1, queryKey);
            try (ResultSet rs = labelStmt.executeQuery()) {
                while (rs.next()) {
                    labels.add(new MailboxLabel(
                        rs.getString("name"),
                        rs.getInt("message_count"),
                        rs.getInt("is_system") == 1
                    ));
                }
            }
        }

        InboxSnapshot snapshot = new InboxSnapshot(emails, labels);
        InboxBootstrap bootstrap = new InboxBootstrap(snapshot, stats, nextPageToken);
        return Optional.of(new CachedInboxData(bootstrap, fullyLoaded, cachedAtEpochMs));
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static String encodeLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            cleaned.add(label.trim());
        }
        return String.join(LABELS_SEPARATOR, cleaned);
    }

    private static List<String> decodeLabels(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(LABELS_SEPARATOR);
        List<String> labels = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            labels.add(part.trim());
        }
        return labels;
    }
}
