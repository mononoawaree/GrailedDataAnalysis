package org.example.grailedmldemo.config;

import java.util.Properties;

public class GrailedApiConfig {
    private static final Properties props = new Properties();
    static {
        try {
            props.load(GrailedApiConfig.class
                    .getClassLoader()
                    .getResourceAsStream("application.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load properties", e);
        }
    }
    public static String getDesignersUrl() {
        return props.getProperty("designers.url");
    }
}
