package org.example.grailedmldemo.config;

import java.io.IOException;
import java.util.Properties;

public class ProxyConfig {
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

    public static String getProxyUsername() {
        return props.getProperty("proxy.username");
    }

    public static String getProxyPassword() {
        return props.getProperty("proxy.password");
    }

}