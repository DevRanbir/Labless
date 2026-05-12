package com.labless.gmail;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.google.api.services.gmail.model.Profile;
import com.labless.model.EmailMessage;
import com.labless.model.EmailPage;
import com.labless.model.MailboxStats;
import com.labless.model.MailboxLabel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;

public class GoogleGmailClient implements GmailClient {
    private final Gmail gmail;
    private final Map<String, String> labelIdByName = new HashMap<>();
    private final Map<String, String> labelNameById = new HashMap<>();
    private final String profileImageUrl;

    public GoogleGmailClient() throws Exception {
        GoogleAuthManager.AuthorizedGmailSession session = GoogleAuthManager.createAuthorizedSession();
        this.gmail = session.gmail();
        this.profileImageUrl = GoogleAuthManager.fetchProfileImageUrl(session.credential(), session.transport());
        refreshLabelCache();
    }

    @Override
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    @Override
    public List<EmailMessage> fetchEmails(String query, int maxResults) {
        try {
            return fetchEmailPage(query, null, maxResults).getEmails();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch Gmail messages: " + ex.getMessage(), ex);
        }
    }

    @Override
    public EmailPage fetchEmailPage(String query, String pageToken, int pageSize) {
        try {
            ListMessagesResponse response = gmail.users().messages()
                .list("me")
                .setQ(query)
                .setPageToken(pageToken)
                .setMaxResults((long) pageSize)
                .execute();

            List<EmailMessage> result = new ArrayList<>();
            if (response.getMessages() == null) {
                return new EmailPage(result, null);
            }
            for (Message messageRef : response.getMessages()) {
                Message message = gmail.users().messages()
                    .get("me", messageRef.getId())
                    .setFormat("full")
                    .execute();
                result.add(toEmailMessage(message));
            }
            return new EmailPage(result, response.getNextPageToken());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch Gmail page: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<MailboxLabel> fetchLabels() {
        try {
            ListLabelsResponse response = gmail.users().labels().list("me").execute();
            List<MailboxLabel> labels = new ArrayList<>();
            if (response.getLabels() == null) {
                return labels;
            }
            for (Label listedLabel : response.getLabels()) {
                if (listedLabel.getId() == null) {
                    continue;
                }
                Label label = gmail.users().labels().get("me", listedLabel.getId()).execute();
                String name = label.getName() == null ? "" : label.getName();
                if (name.isBlank()) {
                    continue;
                }
                int count = label.getMessagesTotal() == null ? 0 : label.getMessagesTotal();
                boolean system = "system".equalsIgnoreCase(label.getType());
                labels.add(new MailboxLabel(name, count, system));
            }
            labels.sort(Comparator
                .comparing(MailboxLabel::isSystem).reversed()
                .thenComparing(MailboxLabel::getName, String.CASE_INSENSITIVE_ORDER));
            return labels;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch Gmail labels: " + ex.getMessage(), ex);
        }
    }

    @Override
    public MailboxStats fetchMailboxStats(String query) {
        try {
            Profile profile = gmail.users().getProfile("me").execute();
            long totalMessages = profile.getMessagesTotal() == null ? 0 : profile.getMessagesTotal();
            long totalThreads = profile.getThreadsTotal() == null ? 0 : profile.getThreadsTotal();
            String emailAddress = profile.getEmailAddress();

            long inboxCount = 0;
            long unreadCount = 0;
            ListLabelsResponse labels = gmail.users().labels().list("me").execute();
            if (labels.getLabels() != null) {
                for (Label listedLabel : labels.getLabels()) {
                    String name = listedLabel.getName() == null ? "" : listedLabel.getName().toUpperCase(Locale.ROOT);
                    if (!"INBOX".equals(name) && !"UNREAD".equals(name)) {
                        continue;
                    }
                    if (listedLabel.getId() == null) {
                        continue;
                    }
                    Label full = gmail.users().labels().get("me", listedLabel.getId()).execute();
                    long count = full.getMessagesTotal() == null ? 0 : full.getMessagesTotal();
                    if ("INBOX".equals(name)) {
                        inboxCount = count;
                    } else if ("UNREAD".equals(name)) {
                        unreadCount = count;
                    }
                }
            }

            long queryCount = 0;
            if (query != null && !query.isBlank()) {
                ListMessagesResponse response = gmail.users().messages()
                    .list("me")
                    .setQ(query)
                    .setMaxResults(1L)
                    .execute();
                if (response.getResultSizeEstimate() != null) {
                    queryCount = response.getResultSizeEstimate();
                }
            } else {
                queryCount = totalMessages;
            }

            return new MailboxStats(emailAddress, totalMessages, totalThreads, inboxCount, unreadCount, queryCount);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to fetch mailbox stats: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void ensureLabel(String labelName) {
        try {
            if (labelIdByName.containsKey(labelName)) {
                return;
            }
            Label created = gmail.users().labels().create("me",
                new Label()
                    .setName(labelName)
                    .setLabelListVisibility("labelShow")
                    .setMessageListVisibility("show")
            ).execute();
            labelIdByName.put(labelName, created.getId());
            labelNameById.put(created.getId(), labelName);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to ensure label '" + labelName + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public void applyLabels(String emailId, List<String> labels) {
        try {
            List<String> labelIds = new ArrayList<>();
            for (String labelName : labels) {
                ensureLabel(labelName);
                String labelId = labelIdByName.get(labelName);
                if (labelId != null) {
                    labelIds.add(labelId);
                }
            }
            if (labelIds.isEmpty()) {
                return;
            }
            ModifyMessageRequest request = new ModifyMessageRequest().setAddLabelIds(labelIds);
            gmail.users().messages().modify("me", emailId, request).execute();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to apply labels: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void applySingleManagedLabel(String emailId, String targetLabel, List<String> managedLabels) {
        try {
            if (targetLabel == null || targetLabel.isBlank()) {
                return;
            }
            ensureLabel(targetLabel);
            String targetId = labelIdByName.get(targetLabel);
            if (targetId == null || targetId.isBlank()) {
                return;
            }

            List<String> removeIds = new ArrayList<>();
            if (managedLabels != null) {
                for (String managed : managedLabels) {
                    if (managed == null || managed.isBlank()) {
                        continue;
                    }
                    String managedId = labelIdByName.get(managed);
                    if (managedId == null || managedId.isBlank()) {
                        continue;
                    }
                    if (!managedId.equals(targetId)) {
                        removeIds.add(managedId);
                    }
                }
            }

            ModifyMessageRequest request = new ModifyMessageRequest()
                .setAddLabelIds(List.of(targetId))
                .setRemoveLabelIds(removeIds);
            gmail.users().messages().modify("me", emailId, request).execute();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to apply single managed label: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void removeFromInbox(String emailId) {
        try {
            ModifyMessageRequest request = new ModifyMessageRequest().setRemoveLabelIds(List.of("INBOX"));
            gmail.users().messages().modify("me", emailId, request).execute();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to remove message from inbox: " + ex.getMessage(), ex);
        }
    }

    private void refreshLabelCache() throws Exception {
        labelIdByName.clear();
        labelNameById.clear();
        ListLabelsResponse response = gmail.users().labels().list("me").execute();
        if (response.getLabels() == null) {
            return;
        }
        for (Label label : response.getLabels()) {
            if (label.getName() != null && label.getId() != null) {
                labelIdByName.put(label.getName(), label.getId());
                labelNameById.put(label.getId(), label.getName());
            }
        }
    }

    private EmailMessage toEmailMessage(Message message) {
        String subject = headerValue(message, "Subject");
        String sender = headerValue(message, "From");
        String date = headerValue(message, "Date");

        String body = extractBody(message.getPayload());
        if (isBlank(body)) {
            body = message.getSnippet() == null ? "" : message.getSnippet();
        }

        List<String> displayLabels = extractDisplayLabels(message.getLabelIds());

        return new EmailMessage(
            message.getId(),
            isBlank(subject) ? "(No Subject)" : subject,
            isBlank(sender) ? "(Unknown Sender)" : sender,
            isBlank(date) ? "" : date,
            body,
            displayLabels,
            message.getLabelIds() != null && message.getLabelIds().contains("UNREAD")
        );
    }

    private List<String> extractDisplayLabels(List<String> labelIds) {
        if (labelIds == null || labelIds.isEmpty()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (String labelId : labelIds) {
            String labelName = labelNameById.get(labelId);
            if (labelName == null || labelName.isBlank()) {
                continue;
            }
            String upper = labelName.toUpperCase(Locale.ROOT);
            // Keep CATEGORY_ labels so sidebar filtering can match them.
            // The UI uses customLabelsOnly() to hide them from display chips.
            // Skip noise virtual flags that aren't real navigable folders
            if (upper.equals("UNREAD") || upper.equals("IMPORTANT") || upper.equals("CHAT")) {
                continue;
            }
            // Include everything else:
            //   - User-created labels (Transaction, Bills & Payments, etc.)
            //   - Real folder labels (INBOX, SENT, SPAM, TRASH, STARRED, DRAFT)
            labels.add(labelName);
        }
        return labels;
    }


    private static String headerValue(Message message, String headerName) {
        if (message.getPayload() == null || message.getPayload().getHeaders() == null) {
            return "";
        }
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (headerName.equalsIgnoreCase(header.getName())) {
                return header.getValue() == null ? "" : header.getValue();
            }
        }
        return "";
    }

    private static String extractBody(MessagePart part) {
        if (part == null) {
            return "";
        }

        String mimeType = part.getMimeType() == null ? "" : part.getMimeType().toLowerCase(Locale.ROOT);
        if (mimeType.startsWith("text/html")) {
            String decoded = decodeBody(part.getBody());
            if (!isBlank(decoded)) {
                return decoded;
            }
        }
        
        // If it's a multipart/alternative, we should look for text/html first
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String childMimeType = child.getMimeType() == null ? "" : child.getMimeType().toLowerCase(Locale.ROOT);
                if (childMimeType.startsWith("text/html")) {
                    String decoded = decodeBody(child.getBody());
                    if (!isBlank(decoded)) {
                        return decoded;
                    }
                }
            }
            // If no text/html, fallback to any other parts
            for (MessagePart child : part.getParts()) {
                String candidate = extractBody(child);
                if (!isBlank(candidate)) {
                    return candidate;
                }
            }
        }

        if (mimeType.startsWith("text/plain")) {
            String decoded = decodeBody(part.getBody());
            if (!isBlank(decoded)) {
                return "<pre style=\"font-family: sans-serif; white-space: pre-wrap;\">" + decoded + "</pre>";
            }
        }

        return "";
    }

    private static String decodeBody(MessagePartBody body) {
        if (body == null || body.getData() == null) {
            return "";
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(body.getData());
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
