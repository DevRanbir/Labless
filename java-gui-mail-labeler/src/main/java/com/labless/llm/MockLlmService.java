package com.labless.llm;

import com.labless.model.CategoryResult;
import com.labless.model.EmailMessage;

import java.util.List;
import java.util.Locale;

public class MockLlmService implements LlmService {
    @Override
    public CategoryResult categorizeEmail(EmailMessage email, List<String> categories) {
        String combined = (email.getSubject() + " " + email.getBody()).toLowerCase(Locale.ROOT);
        String category = "Other";
        String explanation = "Default fallback classification.";

        if (combined.contains("sale") || combined.contains("discount") || combined.contains("offer")) {
            category = "Marketing";
            explanation = "Detected promotional content and sale language.";
        } else if (combined.contains("trip") || combined.contains("friend") || combined.contains("family")) {
            category = "Personal";
            explanation = "Detected personal conversation language.";
        } else if (combined.contains("payroll") || combined.contains("meeting") || combined.contains("project")) {
            category = "Work";
            explanation = "Detected work-related signals.";
        } else if (combined.contains("statement") || combined.contains("invoice") || combined.contains("payment")) {
            category = "Bills & Payments";
            explanation = "Detected billing and payment terms.";
        } else if (combined.contains("newsletter")) {
            category = "Newsletters";
            explanation = "Detected newsletter-like content.";
        }

        if (!categories.contains(category)) {
            category = "Other";
            explanation = "Result category was not in configured categories.";
        }
        return new CategoryResult(category, explanation);
    }
}
