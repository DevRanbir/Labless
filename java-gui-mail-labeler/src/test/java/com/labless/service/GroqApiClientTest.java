package com.labless.service;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

/**
 * Manual test for GroqApiClient.
 * Run this to verify the Groq API connection works.
 */
public class GroqApiClientTest {
    
    @Test
    public void testGroqApiConnection() {
        String apiKey = "YOUR_GROQ_API_KEY_HERE";
        String model = "llama-3.1-8b-instant";
        
        GroqApiClient client = new GroqApiClient(apiKey, model);
        
        List<String> categories = Arrays.asList(
            "Action Required",
            "Bills & Payments",
            "Receipts & Invoices",
            "Subscriptions",
            "Promotions",
            "Personal",
            "Work",
            "Alerts",
            "Account & Security",
            "Travel & Bookings",
            "Events & Invitations",
            "Spam / Low Priority",
            "University",
            "Transaction",
            "Other"
        );
        
        String testEmail = "Subject: Your Amazon order has shipped\n" +
                          "From: shipment-tracking@amazon.com\n\n" +
                          "Your order #123-4567890-1234567 has been shipped and will arrive on May 15, 2026.\n" +
                          "Track your package: https://amazon.com/track/123";
        
        try {
            System.out.println("Testing Groq API connection...");
            System.out.println("API Key: " + apiKey.substring(0, 10) + "...");
            System.out.println("Model: " + model);
            System.out.println("\nTest Email:\n" + testEmail);
            System.out.println("\n" + "=".repeat(80));
            
            GroqApiClient.CategorizationResult result = client.categorizeEmail(testEmail, categories);
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("RESULT:");
            System.out.println("Category: " + result.getCategory());
            System.out.println("Explanation: " + result.getExplanation());
            System.out.println("Success: " + result.isSuccess());
            System.out.println("\n✓ Test completed successfully!");
            
        } catch (Exception e) {
            System.err.println("\n✗ Test failed with error:");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Groq API test failed", e);
        }
    }
}
