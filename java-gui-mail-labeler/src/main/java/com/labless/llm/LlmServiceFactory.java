package com.labless.llm;

import com.labless.model.AppConfig;

public class LlmServiceFactory {
    private LlmServiceFactory() {
    }

    public static LlmService create(AppConfig config) {
        // First implementation uses a deterministic mock service.
        // Real provider clients (OpenAI/Gemini/Groq/Ollama) can plug in here.
        return new MockLlmService();
    }
}
