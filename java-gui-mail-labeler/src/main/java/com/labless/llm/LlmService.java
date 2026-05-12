package com.labless.llm;

import com.labless.model.CategoryResult;
import com.labless.model.EmailMessage;

import java.util.List;

public interface LlmService {
    CategoryResult categorizeEmail(EmailMessage email, List<String> categories);
}
