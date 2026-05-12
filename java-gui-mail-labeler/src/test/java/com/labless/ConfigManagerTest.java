package com.labless;

import com.labless.config.ConfigManager;
import com.labless.model.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigManagerTest {
    @Test
    void validate_shouldFailWhenOtherCategoryMissing() {
        ConfigManager manager = new ConfigManager();
        AppConfig config = AppConfig.defaults();
        config.getCategories().remove("Other");

        assertThrows(IllegalArgumentException.class, () -> manager.validate(config));
    }
}
