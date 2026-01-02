package com.example.WebsocketsAndSSE.controller;

import com.example.WebsocketsAndSSE.service.ChatService;
import com.example.WebsocketsAndSSE.service.SSEService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class LiveController {

    private final SSEService sseService;
    private final ChatService chatService;

    @GetMapping("/stream")
    public SseEmitter stream(){
        return sseService.subscribe();
    }

}
