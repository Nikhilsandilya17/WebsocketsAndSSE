package com.example.WebsocketsAndSSE.controller;

import com.example.WebsocketsAndSSE.config.RedisConfig;
import com.example.WebsocketsAndSSE.model.ChatMessageDto;
import com.example.WebsocketsAndSSE.model.TypingDto;
import com.example.WebsocketsAndSSE.service.ChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final ChatService chatService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    //---Websocket endpoint (Real-time) ---
    @MessageMapping("/send-message")
    public void sendMessage(@Payload ChatMessageDto chatMessageDto){
        chatMessageDto.setTimestamp(System.currentTimeMillis());
        chatService.processMessage(chatMessageDto);
    }

    @MessageMapping("/typing")
    public void typing(@Payload TypingDto typingDto) throws JsonProcessingException {
        // Direct to Redis (Skip MySQL, Skip Kafka)
        String json = objectMapper.writeValueAsString(typingDto);
        redisTemplate.convertAndSend(RedisConfig.TYPING_TOPIC, json);
    }

    //---REST endpoint (Chat history) ---
    @GetMapping("/api/chat/history")
    public ResponseEntity<List<ChatMessageDto>> getHistory(){
        return ResponseEntity.ok(chatService.getChatHistory());
    }
}
