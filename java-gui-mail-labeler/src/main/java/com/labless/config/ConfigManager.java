package com.labless.config;

import com.labless.model.AppConfig;
import com.labless.model.LlmConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final Yaml yaml = new Yaml();

    public ConfigLoadResult loadOrCreate(Path path) throws IOException {
        if (!Files.exists(path)) {
            AppConfig defaults = AppConfig.defaults();
            save(path, defaults);
            return new ConfigLoadResult(defaults, true);
        }
        return new ConfigLoadResult(load(path), false);
    }

    @SuppressWarnings("unchecked")
    public AppConfig load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Config file is not a YAML map");
            }

            AppConfig config = AppConfig.defaults();

            Object llmObj = map.get("llm");
            if (llmObj instanceof Map<?, ?> llmMap) {
                LlmConfig llmConfig = config.getLlm();
                llmConfig.setProvider(asString(llmMap.get("provider"), llmConfig.getProvider()));
                llmConfig.setModel(asString(llmMap.get("model"), llmConfig.getModel()));
                llmConfig.setApiKey(asString(llmMap.get("api_key"), llmConfig.getApiKey()));
                llmConfig.setEndpoint(asString(llmMap.get("endpoint"), llmConfig.getEndpoint()));
            }

            Object categoriesObj = map.get("categories");
            if (categoriesObj instanceof List<?> list) {
                List<String> categories = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) {
                        categories.add(item.toString().trim());
                    }
                }
                config.setCategories(categories);
            }

            config.setBatchSize(asInt(map.get("batch_size"), config.getBatchSize()));
            config.setGmailQuery(asString(map.get("gmail_query"), config.getGmailQuery()));
            config.setDatabasePath(asString(map.get("database_path"), config.getDatabasePath()));
            config.setDryRun(asBoolean(map.get("dry_run"), config.isDryRun()));
            config.setOnboardingCompleted(asBoolean(
                map.get("onboarding_completed"), config.isOnboardingCompleted()));

            validate(config);
            return config;
        }
    }

    public void save(Path path, AppConfig config) throws IOException {
        validate(config);
        Files.createDirectories(path.getParent());

        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> llmMap = new LinkedHashMap<>();
        llmMap.put("provider", config.getLlm().getProvider());
        llmMap.put("model", config.getLlm().getModel());
        llmMap.put("api_key", config.getLlm().getApiKey());
        llmMap.put("endpoint", config.getLlm().getEndpoint());

        data.put("llm", llmMap);
        data.put("categories", config.getCategories());
        data.put("batch_size", config.getBatchSize());
        data.put("gmail_query", config.getGmailQuery());
        data.put("database_path", config.getDatabasePath());
        data.put("dry_run", config.isDryRun());
        data.put("onboarding_completed", config.isOnboardingCompleted());

        try (OutputStream output = Files.newOutputStream(path)) {
            output.write(yaml.dump(data).getBytes());
        }
    }

    public void validate(AppConfig config) {
        if (config.getLlm() == null) {
            throw new IllegalArgumentException("llm section is required");
        }
        if (isBlank(config.getLlm().getProvider())) {
            throw new IllegalArgumentException("llm.provider is required");
        }
        if (isBlank(config.getLlm().getModel())) {
            throw new IllegalArgumentException("llm.model is required");
        }
        if (config.getBatchSize() <= 0 || config.getBatchSize() > 500) {
            throw new IllegalArgumentException("batch_size must be between 1 and 500");
        }
        if (isBlank(config.getGmailQuery())) {
            throw new IllegalArgumentException("gmail_query cannot be blank");
        }
        if (isBlank(config.getDatabasePath())) {
            throw new IllegalArgumentException("database_path cannot be blank");
        }
        if (config.getCategories() == null || config.getCategories().isEmpty()) {
            throw new IllegalArgumentException("categories must include at least one category");
        }
        if (!config.getCategories().contains("Other")) {
            throw new IllegalArgumentException("categories must include 'Other'");
        }
        for (String category : config.getCategories()) {
            if (isBlank(category)) {
                throw new IllegalArgumentException("category names cannot be blank");
            }
            if (category.contains("/")) {
                throw new IllegalArgumentException("category '" + category + "' contains invalid Gmail label character '/'");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static int asInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
