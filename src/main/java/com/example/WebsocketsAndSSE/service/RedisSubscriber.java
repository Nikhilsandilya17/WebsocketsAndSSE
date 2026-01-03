package com.example.WebsocketsAndSSE.service;

import com.example.WebsocketsAndSSE.config.RedisConfig;
import com.example.WebsocketsAndSSE.model.ChatMessageDto;
import com.example.WebsocketsAndSSE.model.TypingDto;
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
            // This will now work perfectly because the input is clean JSON
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());
            if(RedisConfig.TYPING_TOPIC.equals(channel)){
                // Handle typing indicator
                TypingDto typingDto = objectMapper.readValue(body, TypingDto.class);
                webSocketTemplate.convertAndSend("/topic/typing", body);
            }
            else {
                // Handle chat message
                ChatMessageDto chatMessageDto = objectMapper.readValue(body, ChatMessageDto.class);
                webSocketTemplate.convertAndSend("/topic/chat", chatMessageDto);
            }
        } catch (Exception e) {
            log.error("Error parsing redis message", e);
        }
    }

}
