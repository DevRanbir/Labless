package com.labless.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GroqApiClient {
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final Gson gson;
    
    public GroqApiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    public CategorizationResult categorizeEmail(String emailContent, List<String> categories) throws IOException {
        String systemPrompt = "You are an email categorization assistant. Always respond with a valid JSON object containing 'category' and 'explanation' fields. Do not wrap the JSON in markdown code fences or any other formatting — return raw JSON only.";
        
        String userPrompt = String.format(
            "Categorize this email into exactly ONE of these categories:\n\n%s\n\nEmail content:\n%s\n\nRespond with a JSON object (raw JSON only, no markdown):\n{\n    \"explanation\": \"<brief reason for this categorization>\",\n    \"category\": \"<exact category name from the list>\"\n}",
            String.join(", ", categories),
            emailContent.length() > 4000 ? emailContent.substring(0, 4000) + "\n[Email truncated]" : emailContent
        );
        
        // Build messages array properly
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        
        com.google.gson.JsonArray messagesArray = new com.google.gson.JsonArray();
        messagesArray.add(systemMessage);
        messagesArray.add(userMessage);
        
        // Build request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("messages", messagesArray);
        requestBody.addProperty("temperature", 0.3);
        requestBody.addProperty("max_completion_tokens", 220);
        requestBody.addProperty("top_p", 1);
        requestBody.addProperty("stream", false);
        
        String requestJson = gson.toJson(requestBody);
        System.out.println("Groq API Request: " + requestJson.substring(0, Math.min(500, requestJson.length())));
        
        Request request = new Request.Builder()
            .url(GROQ_API_URL)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestJson, JSON))
            .build();
        
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            System.out.println("Groq API Response Code: " + response.code());
            System.out.println("Groq API Response: " + responseBody.substring(0, Math.min(500, responseBody.length())));
            
            if (!response.isSuccessful()) {
                throw new IOException("Groq API error: " + response.code() + " - " + response.message() + " - " + responseBody);
            }
            
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            
            String content = jsonResponse
                .getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
            
            // Parse the categorization result
            return parseCategorizationResult(content, categories);
        }
    }
    
    private CategorizationResult parseCategorizationResult(String content, List<String> categories) {
        try {
            System.out.println("Parsing categorization result: " + content.substring(0, Math.min(200, content.length())));
            
            // Remove markdown code fences if present
            content = content.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            
            JsonObject result = JsonParser.parseString(content).getAsJsonObject();
            String category = result.get("category").getAsString().trim();
            String explanation = result.get("explanation").getAsString();
            
            System.out.println("Parsed - Category: " + category + ", Explanation: " + explanation);
            
            // Validate category
            if (categories.contains(category)) {
                System.out.println("Category matched exactly");
                return new CategorizationResult(category, explanation, true);
            }
            
            // Fuzzy match
            System.out.println("Attempting fuzzy match for category: " + category);
            for (String validCategory : categories) {
                if (validCategory.equalsIgnoreCase(category) || 
                    validCategory.toLowerCase().contains(category.toLowerCase()) ||
                    category.toLowerCase().contains(validCategory.toLowerCase())) {
                    System.out.println("Fuzzy matched to: " + validCategory);
                    return new CategorizationResult(validCategory, explanation, true);
                }
            }
            
            System.out.println("No match found, returning Other");
            return new CategorizationResult("Other", "Unknown category: " + category, false);
        } catch (Exception e) {
            System.err.println("Failed to parse categorization result: " + e.getMessage());
            e.printStackTrace();
            return new CategorizationResult("Other", "Failed to parse response: " + e.getMessage(), false);
        }
    }
    
    public static class CategorizationResult {
        private final String category;
        private final String explanation;
        private final boolean success;
        
        public CategorizationResult(String category, String explanation, boolean success) {
            this.category = category;
            this.explanation = explanation;
            this.success = success;
        }
        
        public String getCategory() {
            return category;
        }
        
        public String getExplanation() {
            return explanation;
        }
        
        public boolean isSuccess() {
            return success;
        }
    }
}
