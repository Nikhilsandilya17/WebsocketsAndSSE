package com.example.WebsocketsAndSSE.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketTickDto implements Serializable {
    private String symbol;
    private BigDecimal price;
    private long timestamp;

}
