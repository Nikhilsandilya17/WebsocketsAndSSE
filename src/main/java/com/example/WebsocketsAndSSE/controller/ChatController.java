package com.example.WebsocketsAndSSE.controller;

import com.example.WebsocketsAndSSE.model.ChatMessageDto;
import com.example.WebsocketsAndSSE.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    //---Websocket endpoint (Real-time) ---
    @MessageMapping("/send-message")
    public void sendMessage(@Payload ChatMessageDto chatMessageDto){
        chatMessageDto.setTimestamp(System.currentTimeMillis());
        chatService.processMessage(chatMessageDto);
    }

    //---REST endpoint (Chat history) ---
    @GetMapping("/api/chat/history")
    public ResponseEntity<List<ChatMessageDto>> getHistory(){
        return ResponseEntity.ok(chatService.getChatHistory());
    }
}
