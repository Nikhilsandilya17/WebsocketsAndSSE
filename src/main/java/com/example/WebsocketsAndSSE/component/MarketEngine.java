package com.example.WebsocketsAndSSE.component;

import com.example.WebsocketsAndSSE.model.MarketTickDto;
import com.example.WebsocketsAndSSE.service.SSEService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class MarketEngine {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SSEService sseService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Scheduled(fixedRate = 1000)
    public void simulate() throws JsonProcessingException {
        double price = 45000 + (random.nextDouble() * 100);
        MarketTickDto marketTickDto = new MarketTickDto("BTC-USD", BigDecimal.valueOf(price), System.currentTimeMillis());
        kafkaTemplate.send("market-data", objectMapper.writeValueAsString(marketTickDto));
    }

    @KafkaListener(topics = "market-data", groupId = "dashboard-group")
    public void consume(String message) throws Exception {
        MarketTickDto marketTickDto = objectMapper.readValue(message, MarketTickDto.class);
        sseService.broadcast(marketTickDto);
    }
}
