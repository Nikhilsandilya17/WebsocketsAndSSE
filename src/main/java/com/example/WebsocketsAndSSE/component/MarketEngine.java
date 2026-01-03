package com.example.WebsocketsAndSSE.component;

import com.example.WebsocketsAndSSE.model.MarketTickDto;
import com.example.WebsocketsAndSSE.service.SSEService;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SSEService sseService;
    private final Random random = new Random();

    @Scheduled(fixedRate = 1000)
    public void generateData() {
        double price = 45000 + (random.nextDouble() * 100);
        MarketTickDto marketTickDto = new MarketTickDto("BTC-USD", BigDecimal.valueOf(price), System.currentTimeMillis());
        kafkaTemplate.send("market-data", marketTickDto);
    }

    @KafkaListener(topics = "market-data", groupId = "dashboard-group")
    public void consume(MarketTickDto marketTickDto) {
        sseService.broadcast(marketTickDto);
    }
}
