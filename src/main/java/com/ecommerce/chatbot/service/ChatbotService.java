// File: src/main/java/com/ecommerce/chatbot/service/ChatbotService.java
package com.ecommerce.chatbot.service;

import com.ecommerce.chatbot.dto.ChatRequest;
import com.ecommerce.chatbot.dto.ChatResponse;
import com.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Chatbot service - designed as an interface for future AI integration.
 *
 * Current: rule-based stub implementation
 * Future: integrate with OpenAI GPT, Anthropic Claude, or RAG pipeline over product catalog
 *
 * Architecture: this service acts as an adapter/facade.
 * Swapping the AI provider only requires changing this service,
 * not the controller or any other layer.
 */
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final OrderRepository orderRepository;

    /**
     * Process user message.
     * - guest (userId=null): catalog-only context
     * - authenticated: catalog + order history context
     */
    public ChatResponse respond(UUID userId, ChatRequest request) {
        String context = buildContext(userId);
        String reply = generateReply(request.message(), context, userId != null);
        return new ChatResponse(reply, userId != null ? "personalized" : "guest");
    }

    private String buildContext(UUID userId) {
        if (userId == null) return "catalog";
        // TODO: Fetch user's recent orders and inject into LLM context
        // orderRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5));
        return "catalog + order history";
    }

    /**
     * Stub reply generator.
     * Replace this method body with your actual AI/LLM integration.
     *
     * Integration pattern:
     * 1. Build a system prompt with product catalog context
     * 2. Add user-specific context if authenticated (order history, preferences)
     * 3. Call LLM API (OpenAI/Anthropic/etc.) with the message
     * 4. Return the response
     */
    private String generateReply(String message, String context, boolean isAuthenticated) {
        String lowerMsg = message.toLowerCase();

        if (lowerMsg.contains("order") && isAuthenticated) {
            return "I can see your recent orders. To check order status, go to My Orders section.";
        }
        if (lowerMsg.contains("laptop")) {
            return "We have a great selection of laptops! You can search by RAM, processor, or budget using our search filters.";
        }
        if (lowerMsg.contains("return") || lowerMsg.contains("refund")) {
            return "Our return policy allows returns within 30 days of purchase. Please contact support with your order number.";
        }

        return "I'm here to help! You can ask me about products, orders, or returns. " +
               (isAuthenticated ? "I can also look up your order history." : "Sign in for personalized assistance.");
    }
}
