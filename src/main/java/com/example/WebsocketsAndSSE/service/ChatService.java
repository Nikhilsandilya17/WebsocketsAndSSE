package com.example.WebsocketsAndSSE.service;

import com.example.WebsocketsAndSSE.config.RedisConfig;
import com.example.WebsocketsAndSSE.entity.ChatMessage;
import com.example.WebsocketsAndSSE.model.ChatMessageDto;
import com.example.WebsocketsAndSSE.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatRepository chatRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void processMessage(ChatMessageDto chatMessageDto){
        //PERSIST: Save to Database
        ChatMessage entity = ChatMessage.builder()
                .sender(chatMessageDto.getSender())
                .content(chatMessageDto.getContent())
                .build();
        chatRepository.save(entity);

        //BROADCAST: Send to Redis (which syncs to Websockets)
        redisTemplate.convertAndSend(RedisConfig.CHAT_TOPIC, chatMessageDto);
    }

    public List<ChatMessageDto> getChatHistory(){
        //Fetch last 50 messages from DB
        return chatRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(entity -> new ChatMessageDto(
                        entity.getSender(),
                        entity.getContent(),
                        Timestamp.valueOf(entity.getCreatedAt()).getTime()))
                .collect(Collectors.toList());

    }

}
