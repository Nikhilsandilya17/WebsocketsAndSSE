package com.example.WebsocketsAndSSE.service;

import com.example.WebsocketsAndSSE.model.ChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;


/**
 * It listens to Redis and pushed the messages to local
 * WebSocket clients.
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate webSocketTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            ChatMessageDto chatMessage = objectMapper.readValue(message.getBody(), ChatMessageDto.class);
            // Push to local websocket clients on /topic/chat
            webSocketTemplate.convertAndSend("/topic/chat", chatMessage);
        } catch (Exception e) {
            log.error("Failed to process redis message", e);
        }
    }

}
