package com.labless.model;

public class CategoryResult {
    private final String category;
    private final String explanation;

    public CategoryResult(String category, String explanation) {
        this.category = category;
        this.explanation = explanation;
    }

    public String getCategory() {
        return category;
    }

    public String getExplanation() {
        return explanation;
    }
}
