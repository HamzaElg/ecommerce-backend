// File: src/main/java/com/ecommerce/chatbot/controller/ChatbotController.java
package com.ecommerce.chatbot.controller;

import com.ecommerce.chatbot.dto.ChatRequest;
import com.ecommerce.chatbot.dto.ChatResponse;
import com.ecommerce.chatbot.service.ChatbotService;
import com.ecommerce.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "AI-powered product assistant")
public class ChatbotController {

    private final ChatbotService chatbotService;

    /**
     * Chatbot endpoint - public (guest mode) and authenticated (personalized mode).
     *
     * Guest mode: answers product/catalog questions
     * Authenticated mode: also includes user order summary for personalized context
     */
    @PostMapping
    @Operation(summary = "Chat with product assistant")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @AuthenticationPrincipal UUID userId,  // null if not authenticated (guest mode)
            @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ApiResponse.success(chatbotService.respond(userId, request)));
    }
}
