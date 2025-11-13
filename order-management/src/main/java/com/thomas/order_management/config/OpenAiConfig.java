// src/main/java/com/thomas/order_management/config/OpenAiConfig.java
package com.thomas.order_management.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Erstellt den ChatClient-Bean.
 * Das OpenAiChatModel wird automatisch von Spring AI (Starter) bereitgestellt.
 */
@Configuration
public class OpenAiConfig {

    @Bean
    public ChatClient chatClient(OpenAiChatModel model) {
        // Spring injiziert hier das auto-konfigurierte OpenAiChatModel
        // und wir erstellen den ChatClient-Wrapper darum.
        return ChatClient.create(model);
    }
}