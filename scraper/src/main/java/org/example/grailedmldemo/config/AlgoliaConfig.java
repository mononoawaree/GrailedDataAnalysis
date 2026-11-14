package org.example.grailedmldemo.config;

import java.io.IOException;
import java.util.Properties;

public class AlgoliaConfig {
    private static final Properties props = new Properties();
    static {
        try {
            props.load(AlgoliaConfig.class
                    .getClassLoader()
                    .getResourceAsStream("application.properties"));
        } catch (IOException e) {
            throw new RuntimeException("Could not load properties", e);
        }
    }

    public static String getBaseUrl() {
        return props.getProperty("base.url");
    }

    public static String getApiKey() {
        return props.getProperty("x-algolia-api-key");
    }

    public static String getAppId() {
        return props.getProperty("x-algolia-app-id");
    }
}