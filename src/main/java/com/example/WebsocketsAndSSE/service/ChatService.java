package com.example.WebsocketsAndSSE.service;


import com.example.WebsocketsAndSSE.config.RedisConfig;
import com.example.WebsocketsAndSSE.entity.ChatMessage;
import com.example.WebsocketsAndSSE.model.ChatMessageDto;
import com.example.WebsocketsAndSSE.repository.ChatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRepository chatRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper; // Inject ObjectMapper

    @Transactional
    public void processMessage(ChatMessageDto dto) {
        try {
            // 1. Save to MySQL (Persistence)
            chatRepository.save(ChatMessage.builder()
                    .sender(dto.getSender())
                    .content(dto.getContent())
                    .build());

            // 2. Publish to Redis (Real-time)
            // FIX: Convert DTO to JSON String manually before sending
            String jsonMessage = objectMapper.writeValueAsString(dto);
            redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC, jsonMessage);

        } catch (Exception e) {
            log.error("Error processing message", e);
        }
    }

    public List<ChatMessageDto> getChatHistory() {
        return chatRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(e -> new ChatMessageDto(e.getSender(), e.getContent(), java.sql.Timestamp.valueOf(e.getCreatedAt()).getTime()))
                .collect(Collectors.toList());
    }
}