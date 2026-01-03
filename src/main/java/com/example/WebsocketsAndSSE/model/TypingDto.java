package com.example.WebsocketsAndSSE.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class TypingDto {
    private String sender;
    private boolean typing;
}

