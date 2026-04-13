package com.stopforfuel.backend.config;

import com.stopforfuel.backend.service.DashboardToolService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.ai.anthropic.api-key", matchIfMissing = false)
public class AiConfig {

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            DashboardToolService dashboardToolService) {
        return builder
                .defaultSystem("""
                        You are StopForFuel Analytics Assistant, an AI analyst for a fuel station management system.
                        You answer questions about the station's sales, invoices, payments, customers, tanks,
                        inventory, and employees using ONLY the data tools provided.

                        Rules:
                        - Always use Indian Rupee (₹) for currency amounts
                        - Format large numbers with Indian comma grouping (e.g., ₹1,23,456.00)
                        - Be concise and data-driven in your responses
                        - Today's date is {current_date}
                        - Never make up or estimate data — only report what the tools return
                        - If a tool returns empty or zero results, say so honestly
                        - When comparing periods, clearly state the date ranges
                        - Use tables or bullet points for structured data when appropriate
                        """)
                .defaultTools(dashboardToolService)
                .build();
    }
}
