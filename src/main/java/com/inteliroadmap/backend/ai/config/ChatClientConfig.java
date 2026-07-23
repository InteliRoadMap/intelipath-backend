package com.inteliroadmap.backend.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a single, ready-to-use {@link ChatClient} built from Spring AI's
 * auto-configured builder. AI components inject this client directly instead of
 * each rebuilding one from {@code ChatClient.Builder}.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
