package com.example.WebsocketsAndSSE.service;

import com.example.WebsocketsAndSSE.model.MarketTickDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SSEService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(){
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); //No timeout for demo purposes
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;

    }

    public void broadcast(MarketTickDto marketTickDto){
        for(SseEmitter sseEmitter: emitters){
            try{
                sseEmitter.send(SseEmitter.event().name("market-tick").data(marketTickDto));
            } catch (Exception e) {
                log.error("Error sending SSE message, removing emitter", e);
                emitters.remove(sseEmitter);
            }
        }
    }
}
