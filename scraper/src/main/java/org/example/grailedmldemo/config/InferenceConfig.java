package org.example.grailedmldemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import java.util.Properties;

@Configuration
public class InferenceConfig {
    private static final Properties props = new Properties();

    @Bean
    RestClient inferenceClient(@Value("${inference.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}


