// File: src/main/java/com/ecommerce/chatbot/dto/ChatRequest.java
package com.ecommerce.chatbot.dto;
import jakarta.validation.constraints.NotBlank;
public record ChatRequest(@NotBlank String message) {}
