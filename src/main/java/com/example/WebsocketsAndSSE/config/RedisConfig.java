package com.example.WebsocketsAndSSE.config;

import com.example.WebsocketsAndSSE.service.RedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.*;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer; // Import this

@Configuration
public class RedisConfig {
    public static final String CHAT_TOPIC = "global-chat-channel";
    public static final String TYPING_TOPIC = "typing-channel";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // FIX: Use String serializer for BOTH keys and values
        // This ensures clean JSON without Java class headers
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());

        return template;
    }

    // ... (Keep the rest of the file exactly the same: topic, messageListener, redisContainer) ...
    @Bean
    public ChannelTopic topic() {
        return new ChannelTopic(CHAT_TOPIC);
    }

    @Bean
    public MessageListenerAdapter messageListener(RedisSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber);
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter messageListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListener, topic());
        container.addMessageListener(messageListener, new ChannelTopic(TYPING_TOPIC));

        return container;
    }
}