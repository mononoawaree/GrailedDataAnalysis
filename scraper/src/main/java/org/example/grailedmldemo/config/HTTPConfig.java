package org.example.grailedmldemo.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class HTTPConfig {
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient().newBuilder().connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(15)).writeTimeout(Duration.ofSeconds(30)).callTimeout(Duration.ofSeconds(60)).build();
    }
}
