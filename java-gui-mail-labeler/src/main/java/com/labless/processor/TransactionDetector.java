package com.labless.processor;

import com.labless.model.EmailMessage;

import java.util.Locale;
import java.util.regex.Pattern;

public class TransactionDetector {
    private static final Pattern SENDER_PATTERN = Pattern.compile(
        ".*(bank|paypal|stripe|razorpay|upi|billing|invoice|payment).*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUBJECT_PATTERN = Pattern.compile(
        ".*(payment|transaction|receipt|invoice|statement|bill).*",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BODY_PATTERN = Pattern.compile(
        ".*(amount|debited|credited|balance|due|account|ref\\s*id|txn).*",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    public boolean isTransactionEmail(EmailMessage email) {
        String sender = safe(email.getSender());
        String subject = safe(email.getSubject());
        String body = safe(email.getBody());
        return SENDER_PATTERN.matcher(sender).matches()
            && SUBJECT_PATTERN.matcher(subject).matches()
            && BODY_PATTERN.matcher(body).matches();
    }

    private static String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
