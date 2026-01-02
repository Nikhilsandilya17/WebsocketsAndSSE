package com.example.WebsocketsAndSSE.repository;

import com.example.WebsocketsAndSSE.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatRepository extends JpaRepository<ChatMessage, Long> {
    //Custom query to fetch last 50 messages ordered by creation time descending
    List<ChatMessage> findTop50ByOrderByCreatedAtDesc();
}
